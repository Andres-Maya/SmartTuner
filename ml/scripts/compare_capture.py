#!/usr/bin/env python3
"""Compara lo que calculó la app en el teléfono con lo que calcula el entrenamiento en el PC.

Las compilaciones de desarrollo guardan cada identificación (audio + números) en el teléfono.
Tráelas al PC y compáralas:

    adb pull /sdcard/Android/data/com.andres.smarttuner/files/captures ml/
    ml/.venv/Scripts/python.exe ml/scripts/compare_capture.py

Si las puntuaciones de YAMNet coinciden, la app y el entrenamiento "ven" lo mismo y los fallos
se deben a los datos. Si no coinciden, hay un error en cómo la app prepara el audio.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from scipy.signal import resample_poly

sys.path.insert(0, str(Path(__file__).resolve().parent))
from simulate_app import load_head, yamnet_label_names  # noqa: E402
from train import Yamnet, load_audio  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
YAMNET_RATE = 16_000


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    denominator = float(np.linalg.norm(a) * np.linalg.norm(b))
    return float(a @ b / denominator) if denominator else 0.0


def top_labels(scores: np.ndarray, names: list[str], count: int = 4) -> str:
    order = np.argsort(scores)[::-1][:count]
    return ", ".join(f"{names[i]} {scores[i]:.2f}" for i in order)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--captures", type=Path, default=REPO / "ml" / "captures")
    parser.add_argument("--head", type=Path, default=REPO / "app" / "src" / "main" / "assets" / "instrument_head.json")
    parser.add_argument("--model", type=Path, default=REPO / "app" / "src" / "main" / "assets" / "yamnet.tflite")
    parser.add_argument("--since", default="", help="solo capturas desde esta fecha, p. ej. 20260916-1200")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    reports = sorted(p for p in args.captures.glob("*.json") if p.stem >= args.since)
    if not reports:
        print(f"No hay capturas en {args.captures}. Tráelas con:")
        print("  adb pull /sdcard/Android/data/com.andres.smarttuner/files/captures ml/")
        return 1

    yamnet = Yamnet(args.model)
    names = yamnet_label_names(args.model)
    _, _, head = load_head(args.head)

    similarity_single, similarity_padded = [], []
    result_counts, category_counts = Counter(), Counter()
    head_agreement = head_total = 0

    for report_path in reports:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        samples, rate = load_audio(report_path.with_suffix(".wav"))
        window_size = rate  # la app analiza ventanas de 1 s
        decision = report.get("decision")
        verdict = f"{decision[0]['instrument']} {decision[0]['probability']:.0%}" if decision else "sin instrumento"
        print(f"\n{report_path.stem}: {len(report['windows'])} ventanas · {len(report['pitchesMidi'])} alturas · app → {verdict}")

        for index, window in enumerate(report["windows"]):
            end = int(window["endSample"])
            clip = samples[max(0, end - window_size) : end]
            resampled = resample_poly(clip, YAMNET_RATE, rate).astype(np.float32)

            # A: una sola ventana de YAMNet, igual que en el entrenamiento.
            single = yamnet.scores(resampled[: yamnet.window])
            # B: además el resto del clip relleno con ceros, como haría MediaPipe si parte el clip.
            remainder = resampled[yamnet.window :]
            padded = (single + yamnet.scores(remainder)) / 2 if len(remainder) else single

            app_scores = np.asarray(window["scores"], dtype=np.float32)
            similarity_single.append(cosine(app_scores, single))
            similarity_padded.append(cosine(app_scores, padded))
            result_counts[window["resultCount"]] += 1
            category_counts[window["categoryCount"]] += 1

            app_head = window.get("head") or {}
            pc_head = head(single)
            if app_head:
                head_total += 1
                head_agreement += max(app_head, key=app_head.get) == max(pc_head, key=pc_head.get)

            if args.verbose:
                print(f"  ventana {index + 1}: similitud A={similarity_single[-1]:.3f} B={similarity_padded[-1]:.3f} "
                      f"· MediaPipe devolvió {window['resultCount']} resultado(s)")
                print(f"    app: {top_labels(app_scores, names)}")
                print(f"    PC : {top_labels(single, names)}")
                if app_head:
                    best_app = max(app_head, key=app_head.get)
                    best_pc = max(pc_head, key=pc_head.get)
                    print(f"    capa en app: {best_app} {app_head[best_app]:.0%} · capa en PC: {best_pc} {pc_head[best_pc]:.0%}")

    if not similarity_single:
        print("\nLas capturas no tienen ventanas con sonido: toca un instrumento mientras la app escucha.")
        return 1

    print("\n================ RESUMEN ================")
    print(f"Ventanas comparadas: {len(similarity_single)}")
    print(f"Resultados de MediaPipe por ventana: {dict(result_counts)}")
    print(f"Clases devueltas por ventana: {dict(category_counts)} (deberían ser 521)")
    print(f"Similitud app vs PC, una ventana (A):      media {np.mean(similarity_single):.3f} · mínima {np.min(similarity_single):.3f}")
    print(f"Similitud app vs PC, con relleno (B):      media {np.mean(similarity_padded):.3f} · mínima {np.min(similarity_padded):.3f}")
    if head_total:
        print(f"La capa entrenada elige lo mismo en app y PC: {head_agreement}/{head_total} ventanas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
