#!/usr/bin/env python3
"""Revisa el dataset de audio antes de entrenar.

Solo usa la biblioteca estándar, así que funciona con cualquier Python 3.9+ (incluido 3.14):

    python ml/scripts/check_dataset.py              # revisa ml/dataset
    python ml/scripts/check_dataset.py --strict     # también falla con advertencias
    python ml/scripts/check_dataset.py --dataset otra/carpeta
"""
from __future__ import annotations

import argparse
import hashlib
import math
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path

CLASSES = ("guitar", "bass", "violin", "viola", "cello", "ukulele", "other", "background")

MIN_SAMPLE_RATE = 16_000
MIN_SECONDS = 1.0
LONG_SECONDS = 120.0
SILENCE_DBFS = -50.0
CLIPPING_RATIO = 0.001
RECOMMENDED_FILES = 30
RECOMMENDED_MINUTES = 5.0
MIN_SESSIONS = 3
IMBALANCE_RATIO = 3.0
MAX_ANALYZED_SAMPLES = 400_000

FORMAT_PCM = 1
FORMAT_FLOAT = 3
FORMAT_EXTENSIBLE = 0xFFFE
AUDIO_EXTENSIONS = {".mp3", ".m4a", ".aac", ".ogg", ".flac", ".opus", ".3gp", ".amr"}


@dataclass
class WavInfo:
    audio_format: int
    channels: int
    sample_rate: int
    bits: int
    data: bytes

    @property
    def frames(self) -> int:
        return len(self.data) // (self.channels * self.bits // 8)

    @property
    def seconds(self) -> float:
        return self.frames / self.sample_rate


@dataclass
class ClassReport:
    files: int = 0
    seconds: float = 0.0
    sessions: set[str] = field(default_factory=set)


def session_of(path: Path) -> str:
    """Todo lo que va antes de "__" identifica la sesión de grabación."""
    return path.stem.split("__", 1)[0] if "__" in path.stem else path.stem


def read_wav(path: Path) -> WavInfo:
    raw = path.read_bytes()
    if len(raw) < 12 or raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise ValueError("no es un WAV válido (falta la cabecera RIFF/WAVE)")

    fmt = None
    data = None
    offset = 12
    while offset + 8 <= len(raw):
        chunk_id = raw[offset:offset + 4]
        size = struct.unpack_from("<I", raw, offset + 4)[0]
        body = raw[offset + 8:offset + 8 + size]
        if chunk_id == b"fmt ":
            audio_format, channels, sample_rate = struct.unpack_from("<HHI", body, 0)
            bits = struct.unpack_from("<H", body, 14)[0]
            if audio_format == FORMAT_EXTENSIBLE and len(body) >= 26:
                audio_format = struct.unpack_from("<H", body, 24)[0]
            fmt = (audio_format, channels, sample_rate, bits)
        elif chunk_id == b"data":
            data = body
        offset += 8 + size + (size % 2)

    if fmt is None:
        raise ValueError("falta el bloque 'fmt '")
    if data is None or not data:
        raise ValueError("no contiene audio (bloque 'data' vacío)")
    audio_format, channels, sample_rate, bits = fmt
    if channels < 1 or sample_rate <= 0 or bits not in (8, 16, 24, 32, 64):
        raise ValueError(f"cabecera no válida (canales={channels}, Hz={sample_rate}, bits={bits})")
    if audio_format not in (FORMAT_PCM, FORMAT_FLOAT):
        raise ValueError(f"formato {audio_format} no soportado: exporta como PCM o float")
    if audio_format == FORMAT_FLOAT and bits not in (32, 64):
        raise ValueError(f"WAV float de {bits} bits no soportado")
    return WavInfo(audio_format, channels, sample_rate, bits, data)


def sample_levels(info: WavInfo) -> tuple[float, float, float]:
    """Pico, RMS (0..1) y fracción saturada del primer canal, submuestreando archivos largos."""
    width = info.bits // 8
    frame_size = width * info.channels
    step = max(1, info.frames // MAX_ANALYZED_SAMPLES)

    if info.audio_format == FORMAT_FLOAT:
        code = "<f" if info.bits == 32 else "<d"
        decode = lambda b: struct.unpack(code, b)[0]  # noqa: E731
    elif info.bits == 8:
        decode = lambda b: (b[0] - 128) / 128.0  # noqa: E731
    else:
        scale = float(1 << (info.bits - 1))
        decode = lambda b: int.from_bytes(b, "little", signed=True) / scale  # noqa: E731

    peak = 0.0
    energy = 0.0
    clipped = 0
    count = 0
    for frame in range(0, info.frames, step):
        start = frame * frame_size
        value = decode(info.data[start:start + width])
        magnitude = abs(value)
        peak = max(peak, magnitude)
        energy += value * value
        clipped += magnitude >= 0.999
        count += 1
    rms = math.sqrt(energy / count) if count else 0.0
    return peak, rms, clipped / count if count else 0.0


def dbfs(value: float) -> float:
    return 20 * math.log10(value) if value > 0 else float("-inf")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dataset", type=Path, default=Path(__file__).resolve().parents[1] / "dataset")
    parser.add_argument("--strict", action="store_true", help="devuelve error también si hay advertencias")
    args = parser.parse_args()
    # La consola de Windows usa cp1252 por defecto y no puede imprimir símbolos como ✓ o ≥.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    dataset: Path = args.dataset
    if not dataset.is_dir():
        print(f"No existe la carpeta del dataset: {dataset}")
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    reports = {name: ClassReport() for name in CLASSES}
    hashes: dict[str, Path] = {}

    for entry in sorted(dataset.iterdir()):
        if entry.is_dir() and entry.name not in CLASSES:
            warnings.append(f"Carpeta desconocida '{entry.name}': se ignora (clases válidas: {', '.join(CLASSES)})")
        elif entry.is_file() and entry.suffix.lower() == ".wav":
            errors.append(f"{entry.name}: está fuera de una carpeta de instrumento")

    for name in CLASSES:
        folder = dataset / name
        if not folder.is_dir():
            continue
        for path in sorted(folder.rglob("*")):
            if not path.is_file() or path.name == ".gitkeep":
                continue
            relative = path.relative_to(dataset)
            if path.suffix.lower() in AUDIO_EXTENSIONS:
                warnings.append(f"{relative}: convierte a .wav, este formato no se usa para entrenar")
                continue
            if path.suffix.lower() != ".wav":
                continue

            try:
                info = read_wav(path)
            except (ValueError, struct.error) as error:
                errors.append(f"{relative}: {error}")
                continue

            digest = hashlib.sha1(info.data).hexdigest()
            if digest in hashes:
                warnings.append(f"{relative}: audio idéntico a {hashes[digest].relative_to(dataset)}")
            hashes.setdefault(digest, path)

            if info.sample_rate < MIN_SAMPLE_RATE:
                errors.append(f"{relative}: {info.sample_rate} Hz es muy poco (mínimo {MIN_SAMPLE_RATE} Hz)")
                continue
            if info.seconds < MIN_SECONDS:
                errors.append(f"{relative}: dura {info.seconds:.2f} s (mínimo {MIN_SECONDS:.0f} s)")
                continue
            if info.seconds > LONG_SECONDS:
                warnings.append(
                    f"{relative}: dura {info.seconds / 60:.1f} min; divídelo en clips de 2–10 s "
                    "con el mismo prefijo de sesión"
                )

            peak, rms, clipped_ratio = sample_levels(info)
            if name != "background" and dbfs(rms) < SILENCE_DBFS:
                warnings.append(f"{relative}: casi silencio ({dbfs(rms):.0f} dBFS)")
            if clipped_ratio > CLIPPING_RATIO:
                warnings.append(f"{relative}: saturado ({clipped_ratio:.1%} de muestras al máximo)")

            report = reports[name]
            report.files += 1
            report.seconds += info.seconds
            report.sessions.add(session_of(path))

    print(f"Dataset: {dataset}\n")
    print(f"{'Clase':<12}{'Archivos':>10}{'Minutos':>10}{'Sesiones':>10}")
    print("-" * 42)
    for name in CLASSES:
        report = reports[name]
        print(f"{name:<12}{report.files:>10}{report.seconds / 60:>10.1f}{len(report.sessions):>10}")
    print()

    filled = {name: r for name, r in reports.items() if r.files}
    for name in CLASSES:
        report = reports[name]
        if report.files == 0:
            warnings.append(f"[{name}] sin archivos: la IA no podrá reconocer esta clase")
            continue
        if report.files < RECOMMENDED_FILES or report.seconds / 60 < RECOMMENDED_MINUTES:
            warnings.append(
                f"[{name}] pocos datos ({report.files} archivos, {report.seconds / 60:.1f} min); "
                f"recomendado ≥ {RECOMMENDED_FILES} archivos y ≥ {RECOMMENDED_MINUTES:.0f} min"
            )
        if len(report.sessions) < MIN_SESSIONS:
            warnings.append(
                f"[{name}] solo {len(report.sessions)} sesión(es); graba en ≥ {MIN_SESSIONS} sesiones "
                "distintas para poder separar entrenamiento y prueba"
            )
    if len(filled) < 2:
        errors.append(
            f"Hay audio en {len(filled)} clase(s); se necesitan al menos 2 para entrenar "
            f"(copia archivos .wav en {dataset}/<clase>/)"
        )
    else:
        longest = max(r.seconds for r in filled.values())
        shortest = min(r.seconds for r in filled.values())
        if shortest > 0 and longest / shortest > IMBALANCE_RATIO:
            warnings.append(
                f"Clases desbalanceadas: la mayor tiene {longest / shortest:.1f}× más audio que la menor "
                f"(ideal ≤ {IMBALANCE_RATIO:.0f}×)"
            )

    for title, items in (("ERRORES", errors), ("ADVERTENCIAS", warnings)):
        if items:
            print(f"{title} ({len(items)}):")
            for item in items:
                print(f"  - {item}")
            print()

    if errors or (args.strict and warnings):
        print("✗ Corrige lo anterior antes de entrenar.")
        return 1
    print("✓ El dataset se puede usar para entrenar." + (" Revisa las advertencias." if warnings else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
