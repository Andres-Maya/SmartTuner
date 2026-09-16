#!/usr/bin/env python3
"""Entrena la capa que distingue instrumentos a partir de las puntuaciones de YAMNet.

Usa el MISMO yamnet.tflite que la app lleva en assets, así que las características de
entrenamiento son idénticas a las que el teléfono calcula en tiempo real. Encima entrena una
regresión logística multinomial (normalización + capa lineal + softmax) que la app aplica
sin dependencias nuevas.

La precisión se mide con **validación cruzada por sesiones**: cada grabación se evalúa una vez
con un modelo que no la vio. El modelo que se instala se entrena después con TODO el audio.

    ml/.venv/Scripts/python.exe ml/scripts/train.py
    ml/.venv/Scripts/python.exe ml/scripts/train.py --dataset ml/.smoke_dataset --no-install

Salida en ml/output/:
    instrument_head.json  capa final (se copia a app/src/main/assets/ salvo --no-install)
    metrics.json          precisión de la validación cruzada, por clase y matriz de confusión
    folds/fold_N.json     capa de cada pliegue con sus archivos de prueba (para simulate_app.py --cv)
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf
from scipy.signal import resample_poly
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dataset import CLASSES, FORMAT_FLOAT, read_wav, session_of  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
YAMNET_RATE = 16_000
HOP_SECONDS = 0.48
SILENCE_RMS = 0.006
MAX_FOLDS = 5
C_GRID = (0.03, 0.1, 0.3, 1.0, 3.0)
TRANSFORMS = ("raw", "log")

# El micrófono del teléfono capta a distintos volúmenes y con ruido de fondo: entrenar con
# copias atenuadas y con ruido evita que el modelo aprenda el volumen en vez del timbre.
AUGMENTATIONS = (
    ("original", 1.0, 0.0),
    ("suave", 0.35, 0.0),
    ("ruidoso", 0.8, 0.005),
)


def load_audio(path: Path) -> tuple[np.ndarray, int]:
    """Devuelve la señal mono en float32 [-1, 1] y su frecuencia de muestreo."""
    info = read_wav(path)
    raw = info.data
    if info.audio_format == FORMAT_FLOAT:
        samples = np.frombuffer(raw, dtype=np.float32 if info.bits == 32 else np.float64).astype(np.float32)
    elif info.bits == 8:
        samples = (np.frombuffer(raw, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
    elif info.bits == 24:
        packed = np.frombuffer(raw[: len(raw) // 3 * 3], dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        values = packed[:, 0] | (packed[:, 1] << 8) | (packed[:, 2] << 16)
        values = np.where(values & 0x800000, values - 0x1000000, values)
        samples = values.astype(np.float32) / 8_388_608.0
    else:
        dtype = {16: np.int16, 32: np.int32, 64: np.int64}[info.bits]
        samples = np.frombuffer(raw, dtype=dtype).astype(np.float32) / float(1 << (info.bits - 1))

    usable = len(samples) - len(samples) % info.channels
    mono = samples[:usable].reshape(-1, info.channels).mean(axis=1)
    return mono.astype(np.float32), info.sample_rate


def to_yamnet_rate(samples: np.ndarray, rate: int) -> np.ndarray:
    if rate == YAMNET_RATE:
        return samples
    divisor = np.gcd(rate, YAMNET_RATE)
    return resample_poly(samples, YAMNET_RATE // divisor, rate // divisor).astype(np.float32)


class Yamnet:
    """yamnet.tflite ejecutado con el intérprete de TensorFlow Lite."""

    def __init__(self, model_path: Path):
        self.interpreter = tf.lite.Interpreter(model_path=str(model_path))
        self.input_detail = self.interpreter.get_input_details()[0]
        self.output_detail = self.interpreter.get_output_details()[0]
        self.window = int(self.input_detail["shape"][-1])
        self.interpreter.allocate_tensors()

    def scores(self, window: np.ndarray) -> np.ndarray:
        block = np.zeros(self.window, dtype=np.float32)
        block[: min(len(window), self.window)] = window[: self.window]
        self.interpreter.set_tensor(self.input_detail["index"], block.reshape(self.input_detail["shape"]))
        self.interpreter.invoke()
        return self.interpreter.get_tensor(self.output_detail["index"]).reshape(-1).astype(np.float32)


def extract_features(dataset: Path, yamnet: Yamnet, labels: list[str], augmentations) -> dict:
    """Una fila por ventana y por aumento. `original` marca las filas sin aumentar (las que se evalúan)."""
    features, targets, files, sessions, original = [], [], [], [], []
    hop = int(HOP_SECONDS * YAMNET_RATE)
    seen: dict[str, str] = {}
    for label_index, label in enumerate(labels):
        for path in sorted((dataset / label).glob("*.wav")):
            key = f"{label}/{path.name}"
            # Un audio repetido con otro nombre contaría como otra sesión y se filtraría a la prueba.
            digest = hashlib.sha1(read_wav(path).data).hexdigest()
            if digest in seen:
                print(f"  {key}: se omite, es idéntico a {seen[digest]}")
                continue
            seen[digest] = key
            samples = to_yamnet_rate(*load_audio(path))
            windows = 0
            for start in range(0, max(1, len(samples) - yamnet.window + 1), hop):
                window = samples[start : start + yamnet.window]
                if float(np.sqrt(np.mean(window**2))) < SILENCE_RMS:
                    continue
                for name, gain, noise in augmentations:
                    augmented = window * gain
                    if noise:
                        augmented = augmented + np.random.default_rng(start).normal(0, noise, len(window))
                    features.append(yamnet.scores(augmented.astype(np.float32)))
                    targets.append(label_index)
                    files.append(key)
                    sessions.append(f"{label}/{session_of(path)}")
                    original.append(name == "original")
                windows += 1
            print(f"  {key}: {windows} ventanas ({len(samples) / YAMNET_RATE:.1f} s)")
    return {
        "features": np.asarray(features, dtype=np.float32),
        "targets": np.asarray(targets, dtype=np.int32),
        "files": np.asarray(files),
        "sessions": np.asarray(sessions),
        "original": np.asarray(original, dtype=bool),
    }


def transform(features: np.ndarray, kind: str) -> np.ndarray:
    return np.log(features + 1e-6) if kind == "log" else features


def fit(features: np.ndarray, targets: np.ndarray, kind: str, c: float):
    transformed = transform(features, kind)
    scaler = StandardScaler().fit(transformed)
    model = LogisticRegression(C=c, max_iter=4000, class_weight="balanced")
    model.fit(scaler.transform(transformed), targets)
    return scaler, model


def predict_proba(scaler, model, features: np.ndarray, kind: str) -> np.ndarray:
    return model.predict_proba(scaler.transform(transform(features, kind)))


def cross_validate(data: dict, folds: list, kind: str, c: float) -> dict:
    """Predicciones fuera de pliegue: cada ventana original la predice un modelo que no vio su sesión."""
    features, targets, original = data["features"], data["targets"], data["original"]
    window_predictions = np.full(len(targets), -1)
    file_predictions: dict[str, int] = {}
    for train_index, test_index in folds:
        scaler, model = fit(features[train_index], targets[train_index], kind, c)
        evaluated = test_index[original[test_index]]
        probabilities = predict_proba(scaler, model, features[evaluated], kind)
        window_predictions[evaluated] = probabilities.argmax(axis=1)
        for name in np.unique(data["files"][evaluated]):
            mask = data["files"][evaluated] == name
            file_predictions[name] = int(probabilities[mask].mean(axis=0).argmax())

    evaluated_all = original & (window_predictions >= 0)
    window_accuracy = float(np.mean(window_predictions[evaluated_all] == targets[evaluated_all]))
    file_targets = {name: int(targets[data["files"] == name][0]) for name in file_predictions}
    file_accuracy = float(np.mean([file_predictions[n] == file_targets[n] for n in file_predictions]))
    return {
        "window_accuracy": window_accuracy,
        "file_accuracy": file_accuracy,
        "window_predictions": window_predictions,
        "file_predictions": file_predictions,
        "file_targets": file_targets,
    }


def head_json(scaler, model, labels: list[str], kind: str, **extra) -> dict:
    return {
        "version": 2,
        "labels": labels,
        "featureTransform": kind,
        "mean": scaler.mean_.astype(np.float32).tolist(),
        "scale": scaler.scale_.astype(np.float32).tolist(),
        "weights": [row.tolist() for row in model.coef_.astype(np.float32)],
        "bias": model.intercept_.astype(np.float32).tolist(),
        **extra,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", type=Path, default=REPO / "ml" / "dataset")
    parser.add_argument("--output", type=Path, default=REPO / "ml" / "output")
    parser.add_argument("--model", type=Path, default=REPO / "app" / "src" / "main" / "assets" / "yamnet.tflite")
    parser.add_argument("--assets", type=Path, default=REPO / "app" / "src" / "main" / "assets")
    parser.add_argument("--no-install", action="store_true", help="no copia el modelo a los assets de la app")
    parser.add_argument("--no-augment", action="store_true", help="entrena solo con el audio original")
    parser.add_argument("--seed", type=int, default=13)
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    augmentations = AUGMENTATIONS[:1] if args.no_augment else AUGMENTATIONS

    labels = [name for name in CLASSES if any((args.dataset / name).glob("*.wav"))]
    if len(labels) < 2:
        print(f"Se necesitan al menos 2 clases con audio en {args.dataset}")
        return 1
    print(f"Clases con datos: {', '.join(labels)}\n")

    yamnet = Yamnet(args.model)
    data = extract_features(args.dataset, yamnet, labels, augmentations)
    if not len(data["features"]):
        print("No se extrajo ninguna ventana: ¿todo el audio está en silencio?")
        return 1

    sessions_per_class = [
        len(set(data["sessions"][data["targets"] == index])) for index in range(len(labels))
    ]
    n_folds = min(MAX_FOLDS, min(sessions_per_class))
    if n_folds < 2:
        weakest = labels[int(np.argmin(sessions_per_class))]
        print(f"La clase '{weakest}' tiene una sola sesión: graba al menos 2 para poder evaluar.")
        return 1
    splitter = StratifiedGroupKFold(n_splits=n_folds, shuffle=True, random_state=args.seed)
    folds = list(splitter.split(data["features"], data["targets"], groups=data["sessions"]))
    originals = int(data["original"].sum())
    print(f"\nVentanas: {originals} originales ({len(data['features'])} con aumentos) · "
          f"validación cruzada de {n_folds} pliegues por sesión\n")

    best = None
    for kind in TRANSFORMS:
        for c in C_GRID:
            result = cross_validate(data, folds, kind, c)
            print(f"  {kind:<4} C={c:<5} ventana {result['window_accuracy']:.3f} · archivo {result['file_accuracy']:.3f}")
            score = (result["file_accuracy"], result["window_accuracy"])
            if best is None or score > best["score"]:
                best = {"kind": kind, "C": c, "score": score, "result": result}

    kind, c, result = best["kind"], best["C"], best["result"]
    print(f"\nMejor configuración: {kind}, C={c}")
    print(f"Validación cruzada · precisión por ventana: {result['window_accuracy']:.3f} · "
          f"por archivo: {result['file_accuracy']:.3f}")

    evaluated = data["original"] & (result["window_predictions"] >= 0)
    matrix = np.zeros((len(labels), len(labels)), dtype=int)
    for real, predicted in zip(data["targets"][evaluated], result["window_predictions"][evaluated]):
        matrix[int(real), int(predicted)] += 1
    print("\nMatriz de confusión por ventana (filas = real, columnas = predicho)")
    print(f"{'':<12}" + "".join(f"{name[:8]:>9}" for name in labels) + "   acierto")
    per_class = {}
    for index, name in enumerate(labels):
        total = matrix[index].sum()
        per_class[name] = float(matrix[index, index] / total) if total else 0.0
        print(f"{name:<12}" + "".join(f"{value:>9}" for value in matrix[index]) + f"{per_class[name]:>10.2f}")

    wrong_files = sorted(
        f"{name} → {labels[predicted]}"
        for name, predicted in result["file_predictions"].items()
        if predicted != result["file_targets"][name]
    )
    if wrong_files:
        print("\nArchivos mal clasificados:")
        for line in wrong_files:
            print(f"  {line}")

    # Capa de cada pliegue, para que simulate_app.py --cv mida la app sin trampa.
    fold_dir = args.output / "folds"
    if fold_dir.exists():
        shutil.rmtree(fold_dir)
    fold_dir.mkdir(parents=True)
    for number, (train_index, test_index) in enumerate(folds, start=1):
        scaler, model = fit(data["features"][train_index], data["targets"][train_index], kind, c)
        test_files = sorted(set(data["files"][test_index].tolist()))
        (fold_dir / f"fold_{number}.json").write_text(
            json.dumps(head_json(scaler, model, labels, kind, testFiles=test_files)), encoding="utf-8"
        )

    # Modelo final con TODO el audio.
    scaler, model = fit(data["features"], data["targets"], kind, c)
    head_path = args.output / "instrument_head.json"
    head_path.write_text(json.dumps(head_json(scaler, model, labels, kind)), encoding="utf-8")
    (args.output / "metrics.json").write_text(
        json.dumps(
            {
                "labels": labels,
                "files": len(result["file_predictions"]),
                "windows": originals,
                "folds": n_folds,
                "augmentations": [name for name, _, _ in augmentations],
                "featureTransform": kind,
                "C": c,
                "cvWindowAccuracy": result["window_accuracy"],
                "cvFileAccuracy": result["file_accuracy"],
                "recallPerClass": per_class,
                "confusionMatrix": matrix.tolist(),
                "misclassifiedFiles": wrong_files,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    stale_split = args.output / "split.json"
    if stale_split.exists():
        stale_split.unlink()
    print(f"\nModelo final (entrenado con todo el audio): {head_path} ({head_path.stat().st_size / 1024:.0f} kB)")

    if not args.no_install:
        args.assets.mkdir(parents=True, exist_ok=True)
        shutil.copy2(head_path, args.assets / "instrument_head.json")
        print(f"Copiado a {args.assets / 'instrument_head.json'}: recompila la app para usarlo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
