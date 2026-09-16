#!/usr/bin/env python3
"""Entrena la capa que distingue instrumentos a partir de las puntuaciones de YAMNet.

Usa el MISMO yamnet.tflite que la app lleva en assets, así que las características de
entrenamiento son idénticas a las que el teléfono calcula en tiempo real. Encima entrena una
regresión logística multinomial (una capa lineal + softmax) que la app aplica sin dependencias nuevas.

    ml/.venv/Scripts/python.exe ml/scripts/train.py
    ml/.venv/Scripts/python.exe ml/scripts/train.py --dataset ml/.smoke_dataset --no-install

Salida en ml/output/:
    instrument_head.json  capa entrenada (se copia a app/src/main/assets/ salvo --no-install)
    metrics.json          precisión y matriz de confusión
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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dataset import CLASSES, FORMAT_FLOAT, read_wav, session_of  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
YAMNET_RATE = 16_000
HOP_SECONDS = 0.48
SILENCE_RMS = 0.006
C_GRID = (0.03, 0.1, 0.3, 1.0, 3.0, 10.0)


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


def extract_features(dataset: Path, yamnet: Yamnet, labels: list[str]) -> dict[str, np.ndarray]:
    features, targets, groups, files = [], [], [], []
    hop = int(HOP_SECONDS * YAMNET_RATE)
    for label_index, label in enumerate(labels):
        for path in sorted((dataset / label).glob("*.wav")):
            samples, rate = load_audio(path)
            samples = to_yamnet_rate(samples, rate)
            windows = 0
            for start in range(0, max(1, len(samples) - yamnet.window + 1), hop):
                window = samples[start : start + yamnet.window]
                if float(np.sqrt(np.mean(window**2))) < SILENCE_RMS:
                    continue
                features.append(yamnet.scores(window))
                targets.append(label_index)
                groups.append(f"{label}/{session_of(path)}")
                files.append(f"{label}/{path.name}")
                windows += 1
            print(f"  {label}/{path.name}: {windows} ventanas ({len(samples) / YAMNET_RATE:.1f} s)")
    return {
        "features": np.asarray(features, dtype=np.float32),
        "targets": np.asarray(targets, dtype=np.int32),
        "groups": np.asarray(groups),
        "files": np.asarray(files),
    }


def split_sessions(groups: np.ndarray, targets: np.ndarray, labels: list[str], seed: int) -> dict[str, np.ndarray]:
    """Reparte por sesión (nunca por ventana) para que no se filtre la misma grabación."""
    rng = random.Random(seed)
    by_label: dict[int, list[str]] = defaultdict(list)
    for group, target in zip(groups, targets):
        if group not in by_label[int(target)]:
            by_label[int(target)].append(group)

    assignment: dict[str, str] = {}
    for target, sessions in by_label.items():
        shuffled = sessions[:]
        rng.shuffle(shuffled)
        if len(shuffled) < 3:
            raise SystemExit(
                f"La clase '{labels[target]}' solo tiene {len(shuffled)} sesión(es); "
                "se necesitan al menos 3 para separar entrenamiento, validación y prueba."
            )
        assignment[shuffled[0]] = "test"
        assignment[shuffled[1]] = "val"
        for session in shuffled[2:]:
            assignment[session] = "train"

    return {name: np.array([assignment[g] == name for g in groups]) for name in ("train", "val", "test")}


def transform(features: np.ndarray, kind: str) -> np.ndarray:
    return np.log(features + 1e-6) if kind == "log" else features


def file_level_accuracy(model, features: np.ndarray, targets: np.ndarray, files: np.ndarray) -> float:
    """Promedia las probabilidades de todas las ventanas de un archivo, como hace la app."""
    probabilities = model.predict_proba(features)
    correct = 0
    unique = np.unique(files)
    for name in unique:
        mask = files == name
        predicted = int(np.argmax(probabilities[mask].mean(axis=0)))
        correct += predicted == int(targets[mask][0])
    return correct / len(unique) if len(unique) else 0.0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", type=Path, default=REPO / "ml" / "dataset")
    parser.add_argument("--output", type=Path, default=REPO / "ml" / "output")
    parser.add_argument("--model", type=Path, default=REPO / "app" / "src" / "main" / "assets" / "yamnet.tflite")
    parser.add_argument("--assets", type=Path, default=REPO / "app" / "src" / "main" / "assets")
    parser.add_argument("--no-install", action="store_true", help="no copia el modelo a los assets de la app")
    parser.add_argument("--seed", type=int, default=13)
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    labels = [name for name in CLASSES if any((args.dataset / name).glob("*.wav"))]
    if len(labels) < 2:
        print(f"Se necesitan al menos 2 clases con audio en {args.dataset}")
        return 1
    print(f"Clases con datos: {', '.join(labels)}\n")

    yamnet = Yamnet(args.model)
    print(f"YAMNet: ventana de {yamnet.window} muestras ({yamnet.window / YAMNET_RATE:.3f} s)\n")
    data = extract_features(args.dataset, yamnet, labels)
    features, targets, groups, files = data["features"], data["targets"], data["groups"], data["files"]
    if not len(features):
        print("No se extrajo ninguna ventana: ¿todo el audio está en silencio?")
        return 1
    print(f"\nVentanas: {len(features)} · características por ventana: {features.shape[1]}")

    masks = split_sessions(groups, targets, labels, args.seed)
    for name, mask in masks.items():
        print(f"  {name}: {int(mask.sum())} ventanas de {len(set(groups[mask]))} sesiones")

    best = None
    for kind in ("raw", "log"):
        transformed = transform(features, kind)
        for c in C_GRID:
            model = LogisticRegression(C=c, max_iter=3000, class_weight="balanced")
            model.fit(transformed[masks["train"]], targets[masks["train"]])
            score = file_level_accuracy(
                model, transformed[masks["val"]], targets[masks["val"]], files[masks["val"]]
            )
            print(f"  {kind:<4} C={c:<5} precisión por archivo (validación) = {score:.3f}")
            if best is None or score > best["val_accuracy"]:
                best = {"kind": kind, "C": c, "model": model, "val_accuracy": score}

    kind, model = best["kind"], best["model"]
    transformed = transform(features, kind)
    print(f"\nMejor configuración: {kind}, C={best['C']}")

    # Reentrena con entrenamiento + validación para aprovechar todo el audio disponible.
    final = LogisticRegression(C=best["C"], max_iter=3000, class_weight="balanced")
    trainval = masks["train"] | masks["val"]
    final.fit(transformed[trainval], targets[trainval])

    test_windows = float(final.score(transformed[masks["test"]], targets[masks["test"]]))
    test_files = file_level_accuracy(final, transformed[masks["test"]], targets[masks["test"]], files[masks["test"]])
    predictions = final.predict(transformed[masks["test"]])
    matrix = np.zeros((len(labels), len(labels)), dtype=int)
    for real, predicted in zip(targets[masks["test"]], predictions):
        matrix[int(real), int(predicted)] += 1

    print(f"\nPrueba · precisión por ventana: {test_windows:.3f} · por archivo: {test_files:.3f}")
    print("\nMatriz de confusión (filas = real, columnas = predicho)")
    print(f"{'':<12}" + "".join(f"{name[:8]:>9}" for name in labels))
    for index, name in enumerate(labels):
        print(f"{name:<12}" + "".join(f"{value:>9}" for value in matrix[index]))

    args.output.mkdir(parents=True, exist_ok=True)
    head = {
        "version": 1,
        "labels": labels,
        "featureTransform": kind,
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
                "featureTransform": kind,
                "C": best["C"],
                "valFileAccuracy": best["val_accuracy"],
                "testWindowAccuracy": test_windows,
                "testFileAccuracy": test_files,
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
