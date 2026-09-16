#!/usr/bin/env python3
"""Reproduce en el PC la decisión completa de la app sobre cada grabación del dataset.

Porta a Python las tres piezas que la app combina: el detector de altura YIN, la capa entrenada
(instrument_head.json) y la fusión con el registro musical. Sirve para depurar por qué la app
se equivoca con un instrumento sin depender del teléfono.

    ml/.venv/Scripts/python.exe ml/scripts/simulate_app.py --cv --seconds 4
    ml/.venv/Scripts/python.exe ml/scripts/simulate_app.py --files cello bass --verbose

Con --cv cada grabación se evalúa con la capa del pliegue que NO la vio (ver train.py):
es la cifra honesta. Sin --cv se usa la capa final, que ya conoce todo el dataset.
"""
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path

import numpy as np
import tensorflow as tf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dataset import CLASSES  # noqa: E402
from train import YAMNET_RATE, Yamnet, load_audio, to_yamnet_rate  # noqa: E402

REPO = Path(__file__).resolve().parents[2]

# --- Constantes que deben coincidir con la app ---------------------------------------------
PITCH_WINDOW = 4096
PITCH_HOP = 2048 * 3  # la app analiza cada 2048; aquí se submuestrea para que el PC sea rápido
YIN_THRESHOLD = 0.12
YIN_MIN_FREQ, YIN_MAX_FREQ = 40.0, 2000.0
YIN_SILENCE_RMS = 0.006
MIN_PROBABILITY = 0.85

SESSION_WINDOW_SECONDS = 1.0
SESSION_HOP_SECONDS = 0.5
SESSION_SILENCE_RMS = 0.006

MIN_EVIDENCE = 0.05
MUSIC_THRESHOLD = 0.15
MIN_PITCHED_FRAMES = 8
PITCH_ERROR_RATE = 0.1
FAMILY_SHARE = 0.1
OPEN_STRING_TOLERANCE = 0.3
HEAD_WEIGHT = 0.7

OPEN_STRINGS = {
    "guitar": [64, 59, 55, 50, 45, 40],
    "bass": [43, 38, 33, 28],
    "violin": [76, 69, 62, 55],
    "viola": [69, 62, 55, 48],
    "cello": [57, 50, 43, 36],
    "ukulele": [69, 64, 60, 67],
}
RANGES = {
    "guitar": (40, 88),
    "bass": (28, 67),
    "violin": (55, 100),
    "viola": (48, 88),
    "cello": (36, 81),
    "ukulele": (60, 84),
    "other": (0, 127),
}
INSTRUMENTS = list(RANGES)
BOWED = ("violin", "viola", "cello")
PLUCKED = ("guitar", "bass", "ukulele")

YAMNET_LABELS = {
    "guitar": {"Guitar", "Electric guitar", "Acoustic guitar", "Steel guitar, slide guitar",
               "Tapping (guitar technique)", "Strum"},
    "bass": {"Bass guitar", "Double bass"},
    "violin": {"Violin, fiddle"},
    "cello": {"Cello"},
    "ukulele": {"Ukulele"},
    "bowed": {"Bowed string instrument", "String section", "Pizzicato"},
    "plucked": {"Plucked string instrument"},
    "music": {"Music", "Musical instrument"},
}
OTHER_INSTRUMENTS = {
    "Banjo", "Sitar", "Mandolin", "Zither", "Harp", "Keyboard (musical)", "Piano", "Electric piano",
    "Organ", "Electronic organ", "Hammond organ", "Synthesizer", "Harpsichord", "Accordion",
    "Drum kit", "Drum", "Snare drum", "Bass drum", "Timpani", "Tabla", "Cymbal", "Percussion",
    "Marimba, xylophone", "Glockenspiel", "Vibraphone", "Steelpan", "Tubular bells",
    "Brass instrument", "Trumpet", "Trombone", "French horn",
    "Wind instrument, woodwind instrument", "Flute", "Saxophone", "Clarinet", "Harmonica",
    "Bagpipes", "Didgeridoo", "Theremin", "Singing", "Choir",
}


# --- Detector de altura (mismo algoritmo que YinPitchDetector.kt) ----------------------------
def yin_pitch(window: np.ndarray, rate: int) -> tuple[float, float] | None:
    """Devuelve (frecuencia, probabilidad) o None, igual que el detector de la app."""
    if float(np.sqrt(np.mean(window**2))) < YIN_SILENCE_RMS:
        return None
    max_tau = int(rate / YIN_MIN_FREQ)
    min_tau = max(2, int(rate / YIN_MAX_FREQ))
    size = len(window) - max_tau - 1

    # Función diferencia acelerada con FFT: d(tau) = p(0) + p(tau) - 2 r(tau)
    x = window.astype(np.float64)
    power = np.concatenate(([0.0], np.cumsum(x**2)))
    head_power = power[size] - power[0]
    tail_power = power[np.arange(1, max_tau + 2) + size] - power[np.arange(1, max_tau + 2)]
    fft_size = 1 << int(np.ceil(np.log2(len(x) + size)))
    spectrum = np.fft.rfft(x[:size], fft_size) * np.conj(np.fft.rfft(x, fft_size))
    correlation = np.fft.irfft(spectrum, fft_size)[: max_tau + 2]

    diff = np.empty(max_tau + 2)
    diff[0] = 1.0
    diff[1:] = head_power + tail_power - 2 * correlation[1:]

    running = np.cumsum(diff[1:])
    taus = np.arange(1, max_tau + 2)
    cmndf = np.ones(max_tau + 2)
    np.divide(diff[1:] * taus, running, out=cmndf[1:], where=running > 0)

    tau = min_tau
    estimate = -1
    while tau <= max_tau:
        if cmndf[tau] < YIN_THRESHOLD:
            while tau + 1 <= max_tau and cmndf[tau + 1] < cmndf[tau]:
                tau += 1
            estimate = tau
            break
        tau += 1
    if estimate < 0:
        return None

    s0, s1, s2 = cmndf[estimate - 1], cmndf[estimate], cmndf[estimate + 1]
    denominator = s0 - 2 * s1 + s2
    refined = estimate if abs(denominator) < 1e-9 else estimate + (s0 - s2) / (2 * denominator)
    return rate / refined, 1.0 - cmndf[estimate]


def detect_pitches(samples: np.ndarray, rate: int) -> list[float]:
    """Alturas MIDI detectadas, como las que la app acumula durante la escucha."""
    pitches = []
    for start in range(0, max(1, len(samples) - PITCH_WINDOW + 1), PITCH_HOP):
        result = yin_pitch(samples[start : start + PITCH_WINDOW], rate)
        if result and result[1] >= MIN_PROBABILITY and result[0] > 0:
            pitches.append(69 + 12 * np.log2(result[0] / 440.0))
    return pitches


# --- Fusión (mismo cálculo que InstrumentFusion.kt) -----------------------------------------
def mean_of_max(windows: list[dict[str, float]], labels: set[str]) -> float:
    return float(np.mean([max((w.get(l, 0.0) for l in labels), default=0.0) for w in windows]))


def pitch_likelihood(instrument: str, pitches: list[float]) -> float:
    if len(pitches) < MIN_PITCHED_FRAMES:
        return 1.0
    low, high = RANGES[instrument]
    out = sum(1 for p in pitches if p < low - 1 or p > high + 1) / len(pitches)
    return PITCH_ERROR_RATE**out


def open_string_bonus(instrument: str, pitches: list[float], octave_tolerant: bool) -> float:
    strings = OPEN_STRINGS.get(instrument)
    if not strings or len(pitches) < MIN_PITCHED_FRAMES:
        return 1.0
    hits = 0
    for pitch in pitches:
        for string in strings:
            offsets = (0, 12) if octave_tolerant else (0,)
            if any(abs(pitch - string - offset) <= OPEN_STRING_TOLERANCE for offset in offsets):
                hits += 1
                break
    return 1.0 + hits / len(pitches)


def distribute(family: float, specific: dict[str, float], weight) -> dict[str, float]:
    weights = {name: (score + FAMILY_SHARE * family) * weight(name) for name, score in specific.items()}
    total = sum(weights.values())
    return {name: (family * value / total if total > 0 else 0.0) for name, value in weights.items()}


def fuse(windows, pitches, head_windows, octave_tolerant: bool, register_power: float,
         head_register: str = "full", head_weight: float = HEAD_WEIGHT, generic_other: str = "keep",
         head_confidence: str = "scaled"):
    head = {}
    if head_windows:
        keys = head_windows[0].keys()
        head = {k: float(np.mean([w[k] for w in head_windows])) for k in keys}
        if head and max(head, key=head.get) == "background":
            return None, head, {}

    scores_by_label = {name: mean_of_max(windows, YAMNET_LABELS[name]) for name in
                       ("guitar", "bass", "violin", "cello", "ukulele", "bowed", "plucked", "music")}
    bowed_family = max(scores_by_label["bowed"], scores_by_label["violin"], scores_by_label["cello"])
    plucked_family = max(scores_by_label["plucked"], scores_by_label["guitar"],
                         scores_by_label["bass"], scores_by_label["ukulele"])
    other = max((mean_of_max(windows, {label}) for label in OTHER_INSTRUMENTS), default=0.0)

    def weight(name: str) -> float:
        return (pitch_likelihood(name, pitches) * open_string_bonus(name, pitches, octave_tolerant)) ** register_power

    scores = {}
    scores.update(distribute(bowed_family, {
        "violin": scores_by_label["violin"],
        "viola": (scores_by_label["violin"] + scores_by_label["cello"]) / 2,
        "cello": scores_by_label["cello"],
    }, weight))
    scores.update(distribute(plucked_family, {
        "guitar": scores_by_label["guitar"],
        "bass": scores_by_label["bass"],
        "ukulele": scores_by_label["ukulele"],
    }, weight))
    if head and "other" in head and generic_other != "keep":
        other = 0.0 if generic_other == "drop" else other * 0.5
    scores["other"] = other

    if max(scores.values()) < MIN_EVIDENCE and scores_by_label["music"] < MUSIC_THRESHOLD:
        return None, head, scores

    if head:
        def head_weight_of(name: str) -> float:
            if head_register == "none":
                return 1.0
            if head_register == "range":
                return pitch_likelihood(name, pitches)
            return weight(name)

        learned = {name: head.get(name, 0.0) * head_weight_of(name) for name in scores}
        learned_total = sum(learned.values())
        if learned_total > 0:
            blend_weight = head_weight * (min(1.0, max(head.values())) if head_confidence == "scaled" else 1.0)
            generic_total = sum(scores.values())
            scores = {
                name: blend_weight * learned[name] / learned_total
                + (1 - blend_weight) * (scores[name] / generic_total if generic_total else 0.0)
                for name in scores
            }
    total = sum(scores.values())
    if total <= 0:
        return None, head, scores
    return {name: value / total for name, value in scores.items()}, head, scores


def load_head(path: Path):
    """Devuelve (etiquetas, archivos de prueba, función ventana → probabilidades) de un instrument_head.json."""
    model = json.loads(path.read_text(encoding="utf-8"))
    labels = model["labels"]
    weights = np.asarray(model["weights"], dtype=np.float64)
    bias = np.asarray(model["bias"], dtype=np.float64)
    mean = np.asarray(model.get("mean", []), dtype=np.float64)
    scale = np.asarray(model.get("scale", []), dtype=np.float64)
    log_features = model.get("featureTransform") == "log"

    def probabilities(scores: np.ndarray) -> dict[str, float]:
        features = np.log(scores + 1e-6) if log_features else scores
        if mean.size:
            features = (features - mean) / np.where(scale > 0, scale, 1.0)
        logits = weights @ features + bias
        exponentials = np.exp(logits - logits.max())
        return dict(zip(labels, exponentials / exponentials.sum()))

    return labels, set(model.get("testFiles", [])), probabilities


def yamnet_label_names(model_path: Path) -> list[str]:
    """Los nombres de las 521 clases vienen dentro del .tflite, como metadatos."""
    with zipfile.ZipFile(model_path) as archive:
        name = next(n for n in archive.namelist() if n.endswith(".txt"))
        return archive.read(name).decode("utf-8").splitlines()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", type=Path, default=REPO / "ml" / "dataset")
    parser.add_argument("--head", type=Path, default=REPO / "ml" / "output" / "instrument_head.json")
    parser.add_argument("--model", type=Path, default=REPO / "app" / "src" / "main" / "assets" / "yamnet.tflite")
    parser.add_argument("--files", nargs="*", default=None, help="clases a simular (por defecto todas)")
    parser.add_argument("--octave-tolerant", action=argparse.BooleanOptionalAction, default=True,
                        help="cuerdas al aire tolerantes a la octava (como la app)")
    parser.add_argument("--register-power", type=float, default=1.0, help="exponente del peso por registro")
    parser.add_argument("--head-register", choices=("full", "range", "none"), default="range",
                        help="qué parte del registro multiplica a la capa entrenada")
    parser.add_argument("--head-weight", type=float, default=HEAD_WEIGHT)
    parser.add_argument("--head-confidence", choices=("scaled", "fixed"), default="fixed",
                        help="scaled: el peso de la capa crece con su confianza; fixed: siempre el mismo")
    parser.add_argument("--generic-other", choices=("keep", "half", "drop"), default="drop",
                        help="qué hacer con el 'Otro' de YAMNet cuando la capa propia ya tiene esa clase")
    parser.add_argument("--cv", action="store_true",
                        help="evalúa cada archivo con la capa del pliegue que no lo vio (honesto)")
    parser.add_argument("--seconds", type=float, default=0.0,
                        help="analiza solo los primeros segundos, como hace la app (0 = todo)")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    if args.cv:
        fold_paths = sorted((REPO / "ml" / "output" / "folds").glob("fold_*.json"))
        if not fold_paths:
            print("No hay pliegues: ejecuta primero ml/scripts/train.py")
            return 1
        heads = [load_head(path) for path in fold_paths]
    else:
        heads = [load_head(args.head)]

    def head_for(key: str):
        if not args.cv:
            return heads[0][2]
        return next((fn for _, test_files, fn in heads if key in test_files), None)

    yamnet = Yamnet(args.model)
    label_names = yamnet_label_names(args.model)
    class_names = args.files or [c for c in CLASSES if (args.dataset / c).is_dir()]
    matrix = Counter()
    head_matrix = Counter()
    total = correct = head_correct = 0

    for label in class_names:
        for path in sorted((args.dataset / label).glob("*.wav")):
            head_probabilities = head_for(f"{label}/{path.name}")
            if head_probabilities is None:
                continue
            raw, rate = load_audio(path)
            if args.seconds:
                raw = raw[: int(args.seconds * rate)]
            pitches = detect_pitches(raw, rate)
            samples = to_yamnet_rate(raw, rate)

            window_labels, window_heads = [], []
            hop = int(SESSION_HOP_SECONDS * YAMNET_RATE)
            for start in range(0, max(1, len(samples) - yamnet.window + 1), hop):
                window = samples[start : start + yamnet.window]
                if float(np.sqrt(np.mean(window**2))) < SESSION_SILENCE_RMS:
                    continue
                scores = yamnet.scores(window)
                window_labels.append(scores)
                window_heads.append(head_probabilities(scores))

            if not window_labels:
                print(f"{label}/{path.name}: sin ventanas con sonido")
                continue

            named_windows = [dict(zip(label_names, w)) for w in window_labels]

            probabilities, head_mean, _ = fuse(
                named_windows, pitches, window_heads, args.octave_tolerant, args.register_power,
                args.head_register, args.head_weight, args.generic_other, args.head_confidence,
            )
            head_best = max(head_mean, key=head_mean.get) if head_mean else "?"
            best = max(probabilities, key=probabilities.get) if probabilities else "background"

            total += 1
            correct += best == label
            head_correct += head_best == label
            matrix[(label, best)] += 1
            head_matrix[(label, head_best)] += 1

            if args.verbose or best != label:
                median = float(np.median(pitches)) if pitches else float("nan")
                notes = Counter(int(round(p)) % 12 for p in pitches)
                top = ", ".join(f"{name} {probabilities[name]:.0%}" for name in
                                sorted(probabilities, key=probabilities.get, reverse=True)[:3]) if probabilities else "—"
                print(f"{label}/{path.name}: app={best} capa={head_best} ({head_mean.get(label, 0):.0%} a {label})")
                print(f"    alturas: {len(pitches)} · mediana MIDI {median:.1f} · clases {notes.most_common(3)}")
                print(f"    app: {top}")

    mode = "validación cruzada (honesto)" if args.cv else "capa final (optimista: ya vio estos archivos)"
    print(f"\nModo: {mode}")
    print(f"Capa entrenada sola: {head_correct}/{total} archivos")
    print(f"App completa:        {correct}/{total} archivos")
    print("\nErrores de la app (real → predicho):")
    for (real, predicted), count in sorted(matrix.items()):
        if real != predicted:
            print(f"  {real:<12} → {predicted:<12} {count}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
