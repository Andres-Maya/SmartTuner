#!/usr/bin/env python3
"""Genera un dataset sintético pequeño para probar que el entrenamiento funciona de punta a punta.

NO sirve para entrenar la IA real: los sonidos son imitaciones simples. Solo usa la biblioteca estándar.

    python ml/scripts/make_smoke_dataset.py --output ml/.smoke_dataset
"""
from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22_050

# Rangos en MIDI (69 = La4) coherentes con Instrument.kt de la app.
RANGES = {
    "guitar": (40, 76),
    "bass": (28, 55),
    "violin": (55, 88),
    "viola": (48, 76),
    "cello": (36, 69),
    "ukulele": (60, 81),
}


def midi_to_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def plucked(freq: float, seconds: float, rng: random.Random, decay: float, brightness: float) -> list[float]:
    """Karplus-Strong: cuerda pulsada."""
    period = max(2, int(RATE / freq))
    buffer = [rng.uniform(-1, 1) for _ in range(period)]
    out = []
    for i in range(int(seconds * RATE)):
        index = i % period
        value = buffer[index]
        out.append(value)
        buffer[index] = decay * (brightness * value + (1 - brightness) * buffer[(index + 1) % period])
    return out


def bowed(freq: float, seconds: float, rng: random.Random, harmonics_rolloff: float) -> list[float]:
    """Diente de sierra filtrado con vibrato y ataque suave: cuerda frotada."""
    vibrato_rate = rng.uniform(4.5, 6.5)
    vibrato_depth = rng.uniform(0.002, 0.006)
    harmonics = [k for k in range(1, 16) if k * freq < 6000]
    out = []
    phase = 0.0
    for i in range(int(seconds * RATE)):
        t = i / RATE
        instantaneous = freq * (1 + vibrato_depth * math.sin(2 * math.pi * vibrato_rate * t))
        phase += 2 * math.pi * instantaneous / RATE
        envelope = min(1.0, t / 0.15)
        out.append(envelope * sum(math.sin(k * phase) / k ** harmonics_rolloff for k in harmonics) * 0.5)
    return out


def other(seconds: float, rng: random.Random) -> list[float]:
    """Acorde con decaimiento exponencial, parecido a un piano."""
    root = rng.uniform(48, 72)
    decay = rng.uniform(1.5, 4.0)
    freqs = [midi_to_hz(root + interval) for interval in (0, rng.choice((3, 4)), 7)]
    gains = [rng.uniform(0.6, 1.0) for _ in freqs]
    return [
        sum(g * math.sin(2 * math.pi * f * i / RATE) for f, g in zip(freqs, gains)) * math.exp(-decay * i / RATE) / 3
        for i in range(int(seconds * RATE))
    ]


def background(seconds: float, rng: random.Random) -> list[float]:
    """Ruido de sala con algún clic."""
    out = [rng.gauss(0, 0.02) for _ in range(int(seconds * RATE))]
    for _ in range(rng.randint(0, 3)):
        position = rng.randrange(len(out))
        out[position] = rng.uniform(-0.6, 0.6)
    return out


def synthesize(label: str, seconds: float, rng: random.Random) -> list[float]:
    if label == "other":
        return other(seconds, rng)
    if label == "background":
        return background(seconds, rng)
    freq = midi_to_hz(rng.randint(*RANGES[label]))
    if label == "guitar":
        return plucked(freq, seconds, rng, decay=0.996, brightness=0.5)
    if label == "bass":
        return plucked(freq, seconds, rng, decay=0.998, brightness=0.8)
    if label == "ukulele":
        return plucked(freq, seconds, rng, decay=0.990, brightness=0.3)
    rolloff = {"violin": 0.8, "viola": 1.0, "cello": 1.2}[label]
    return bowed(freq, seconds, rng, rolloff)


def write_wav(path: Path, samples: list[float]) -> None:
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.8 / peak
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", int(s * scale * 32767)) for s in samples))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / ".smoke_dataset")
    parser.add_argument("--files-per-class", type=int, default=16)
    parser.add_argument("--sessions", type=int, default=4)
    parser.add_argument("--seconds", type=float, default=2.0)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    labels = [*RANGES, "other", "background"]
    for label in labels:
        folder = args.output / label
        folder.mkdir(parents=True, exist_ok=True)
        for index in range(args.files_per_class):
            session = index % args.sessions
            write_wav(folder / f"sintetico{session}__{label}_{index:02d}.wav", synthesize(label, args.seconds, rng))
        print(f"{label}: {args.files_per_class} archivos")
    print(f"Listo: {args.output}")


if __name__ == "__main__":
    main()
