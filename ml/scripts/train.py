#!/usr/bin/env python3
"""Entrena la capa que distingue instrumentos a partir de las puntuaciones de YAMNet.

Usa el MISMO yamnet.tflite que la app lleva en assets, así que las características de
entrenamiento son idénticas a las que el teléfono calcula en tiempo real. Encima entrena una
regresión logística multinomial (normalización + capa lineal + softmax) que la app aplica
sin dependencias nuevas.

    ml/.venv/Scripts/python.exe ml/scripts/train.py
    ml/.venv/Scripts/python.exe ml/scripts/train.py --dataset ml/.smoke_dataset --no-install

Salida en ml/output/:
    instrument_head.json  capa entrenada (se copia a app/src/main/assets/ salvo --no-install)
    metrics.json          precisión, precisión por clase y matriz de confusión
    split.json            qué archivos fueron a entrenamiento, validación y prueba
"""
from __future__ import annotations

import argparse
import json
import random
import shutil
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
import tensorflow as tf
from scipy.signal import resample_poly
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dataset import CLASSES, FORMAT_FLOAT, read_wav, session_of  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
YAMNET_RATE = 16_000
HOP_SECONDS = 0.48
SILENCE_RMS = 0.006
C_GRID = (0.01, 0.03, 0.1, 0.3, 1.0, 3.0)

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


def split_files(dataset: Path, labels: list[str], seed: int) -> dict[str, str]:
    """Reparte por sesión (nunca por ventana) para que no se filtre la misma grabación."""
    rng = random.Random(seed)
    assignment: dict[str, str] = {}
    for label in labels:
        sessions: dict[str, list[Path]] = defaultdict(list)
        for path in sorted((dataset / label).glob("*.wav")):
            sessions[session_of(path)].append(path)
        keys = list(sessions)
        rng.shuffle(keys)
        if len(keys) < 3:
            raise SystemExit(
                f"La clase '{label}' solo tiene {len(keys)} sesión(es); se necesitan al menos 3 "
                "para separar entrenamiento, validación y prueba."
            )
        for index, key in enumerate(keys):
            part = "test" if index == 0 else "val" if index == 1 else "train"
            for path in sessions[key]:
                assignment[f"{label}/{path.name}"] = part
    return assignment


def extract_features(dataset: Path, yamnet: Yamnet, labels: list[str], assignment: dict[str, str]) -> dict:
    features, targets, parts, files = [], [], [], []
    hop = int(HOP_SECONDS * YAMNET_RATE)
    for label_index, label in enumerate(labels):
        for path in sorted((dataset / label).glob("*.wav")):
            key = f"{label}/{path.name}"
            part = assignment[key]
            samples = to_yamnet_rate(*load_audio(path))
            # Solo el conjunto de entrenamiento se aumenta; prueba y validación quedan limpios.
            variants = AUGMENTATIONS if part == "train" else AUGMENTATIONS[:1]
            windows = 0
            for start in range(0, max(1, len(samples) - yamnet.window + 1), hop):
                window = samples[start : start + yamnet.window]
                if float(np.sqrt(np.mean(window**2))) < SILENCE_RMS:
                    continue
                for _, gain, noise in variants:
                    augmented = window * gain
                    if noise:
                        augmented = augmented + np.random.default_rng(start).normal(0, noise, len(window))
                    features.append(yamnet.scores(augmented.astype(np.float32)))
                    targets.append(label_index)
                    parts.append(part)
                    files.append(key)
                windows += 1
            print(f"  {label}/{path.name}: {windows} ventanas ({len(samples) / YAMNET_RATE:.1f} s) → {part}")
    return {
        "features": np.asarray(features, dtype=np.float32),
        "targets": np.asarray(targets, dtype=np.int32),
        "parts": np.asarray(parts),
        "files": np.asarray(files),
    }


def transform(features: np.ndarray, kind: str) -> np.ndarray:
    return np.log(features + 1e-6) if kind == "log" else features


def accuracies(model, scaler, features, targets, files) -> tuple[float, float]:
    """Precisión por ventana y por archivo (promediando probabilidades, como hace la app)."""
    scaled = scaler.transform(features)
    window_accuracy = float(np.mean(model.predict(scaled) == targets))
    probabilities = model.predict_proba(scaled)
    correct = 0
    unique = np.unique(files)
    for name in unique:
        mask = files == name
        correct += int(np.argmax(probabilities[mask].mean(axis=0))) == int(targets[mask][0])
    return window_accuracy, (correct / len(unique) if len(unique) else 0.0)


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
    if args.no_augment:
        global AUGMENTATIONS
        AUGMENTATIONS = AUGMENTATIONS[:1]

    labels = [name for name in CLASSES if any((args.dataset / name).glob("*.wav"))]
    if len(labels) < 2:
        print(f"Se necesitan al menos 2 clases con audio en {args.dataset}")
        return 1
    print(f"Clases con datos: {', '.join(labels)}\n")

    yamnet = Yamnet(args.model)
    print(f"YAMNet: ventana de {yamnet.window} muestras ({yamnet.window / YAMNET_RATE:.3f} s)\n")
    assignment = split_files(args.dataset, labels, args.seed)
    data = extract_features(args.dataset, yamnet, labels, assignment)
    features, targets, parts, files = data["features"], data["targets"], data["parts"], data["files"]
    if not len(features):
        print("No se extrajo ninguna ventana: ¿todo el audio está en silencio?")
        return 1

    masks = {name: parts == name for name in ("train", "val", "test")}
    print(f"\nVentanas: {len(features)} (entrenamiento aumentado ×{len(AUGMENTATIONS)})")
    for name, mask in masks.items():
        print(f"  {name}: {int(mask.sum())} ventanas de {len(set(files[mask]))} archivos")

    best = None
    for kind in ("raw", "log"):
        transformed = transform(features, kind)
        scaler = StandardScaler().fit(transformed[masks["train"]])
        scaled_train = scaler.transform(transformed[masks["train"]])
        for c in C_GRID:
            model = LogisticRegression(C=c, max_iter=4000, class_weight="balanced")
            model.fit(scaled_train, targets[masks["train"]])
            window_accuracy, file_accuracy = accuracies(
                model, scaler, transform(features[masks["val"]], kind), targets[masks["val"]], files[masks["val"]]
            )
            print(f"  {kind:<4} C={c:<5} validación · ventana {window_accuracy:.3f} · archivo {file_accuracy:.3f}")
            score = (file_accuracy, window_accuracy)
            if best is None or score > best["score"]:
                best = {"kind": kind, "C": c, "score": score}

    kind, c = best["kind"], best["C"]
    print(f"\nMejor configuración: {kind}, C={c}")

    # Reentrena con entrenamiento + validación para aprovechar todo el audio disponible.
    transformed = transform(features, kind)
    trainval = masks["train"] | masks["val"]
    scaler = StandardScaler().fit(transformed[trainval])
    final = LogisticRegression(C=c, max_iter=4000, class_weight="balanced")
    final.fit(scaler.transform(transformed[trainval]), targets[trainval])

    test_windows, test_files = accuracies(
        final, scaler, transformed[masks["test"]], targets[masks["test"]], files[masks["test"]]
    )
    predictions = final.predict(scaler.transform(transformed[masks["test"]]))
    matrix = np.zeros((len(labels), len(labels)), dtype=int)
    for real, predicted in zip(targets[masks["test"]], predictions):
        matrix[int(real), int(predicted)] += 1

    print(f"\nPrueba · precisión por ventana: {test_windows:.3f} · por archivo: {test_files:.3f}")
    print("\nMatriz de confusión (filas = real, columnas = predicho)")
    print(f"{'':<12}" + "".join(f"{name[:8]:>9}" for name in labels) + "   acierto")
    per_class = {}
    for index, name in enumerate(labels):
        total = matrix[index].sum()
        recall = matrix[index, index] / total if total else 0.0
        per_class[name] = recall
        print(f"{name:<12}" + "".join(f"{value:>9}" for value in matrix[index]) + f"{recall:>10.2f}")

    args.output.mkdir(parents=True, exist_ok=True)
    head = {
        "version": 2,
        "labels": labels,
        "featureTransform": kind,
        "mean": scaler.mean_.astype(np.float32).tolist(),
        "scale": scaler.scale_.astype(np.float32).tolist(),
        "weights": [row.tolist() for row in final.coef_.astype(np.float32)],
        "bias": final.intercept_.astype(np.float32).tolist(),
    }
    head_path = args.output / "instrument_head.json"
    head_path.write_text(json.dumps(head), encoding="utf-8")
    (args.output / "metrics.json").write_text(
        json.dumps(
            {
                "labels": labels,
                "windows": int(len(features)),
                "augmentations": [name for name, _, _ in AUGMENTATIONS],
                "featureTransform": kind,
                "C": c,
                "valFileAccuracy": best["score"][0],
                "testWindowAccuracy": test_windows,
                "testFileAccuracy": test_files,
                "recallPerClass": per_class,
                "confusionMatrix": matrix.tolist(),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    (args.output / "split.json").write_text(
        json.dumps({name: sorted(set(files[mask].tolist())) for name, mask in masks.items()}, indent=2),
        encoding="utf-8",
    )
    print(f"\nModelo: {head_path} ({head_path.stat().st_size / 1024:.0f} kB)")

    if not args.no_install:
        args.assets.mkdir(parents=True, exist_ok=True)
        shutil.copy2(head_path, args.assets / "instrument_head.json")
        print(f"Copiado a {args.assets / 'instrument_head.json'}: recompila la app para usarlo.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
