#!/usr/bin/env python3
"""Pasa capturas de la app al dataset, con la etiqueta del instrumento que sonaba.

Las compilaciones de desarrollo guardan el audio de cada "Identificar instrumento" tal como lo
capta el teléfono. Es el mejor material de entrenamiento: suena exactamente como lo oirá la app.

    # 1. Toca un instrumento y pulsa "Identificar instrumento" varias veces
    # 2. Trae las capturas al PC (en Git Bash anteponer MSYS_NO_PATHCONV=1)
    adb pull /sdcard/Android/data/com.andres.smarttuner/files/captures ml/
    # 3. Etiqueta las capturas de ese rato (fecha-hora de inicio y fin, incluidas)
    python ml/scripts/import_captures.py cello --since 20260916-1300 --until 20260916-1310

Solo usa la biblioteca estándar.
"""
from __future__ import annotations

import argparse
import shutil
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dataset import CLASSES, MIN_SECONDS, read_wav  # noqa: E402

REPO = Path(__file__).resolve().parents[2]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("label", choices=CLASSES, help="instrumento que sonaba en esas capturas")
    parser.add_argument("--since", required=True, help="primera captura, p. ej. 20260916-1300")
    parser.add_argument("--until", default="99999999-999999", help="última captura (incluida)")
    parser.add_argument("--captures", type=Path, default=REPO / "ml" / "captures")
    parser.add_argument("--dataset", type=Path, default=REPO / "ml" / "dataset")
    parser.add_argument("--session", default="", help="prefijo de sesión (por defecto: app-<día>)")
    parser.add_argument("--dry-run", action="store_true", help="muestra qué copiaría sin copiar")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    until = args.until if len(args.until) >= 15 else args.until + "9" * (15 - len(args.until))
    captures = [
        path for path in sorted(args.captures.glob("*.wav"))
        if args.since <= path.stem <= until
    ]
    if not captures:
        print(f"No hay capturas entre {args.since} y {args.until} en {args.captures}")
        return 1

    destination = args.dataset / args.label
    destination.mkdir(parents=True, exist_ok=True)
    copied = skipped = 0
    for path in captures:
        try:
            info = read_wav(path)
        except (ValueError, struct.error) as error:
            print(f"  {path.name}: se omite ({error})")
            skipped += 1
            continue
        if info.seconds < MIN_SECONDS:
            print(f"  {path.name}: se omite, dura {info.seconds:.1f} s")
            skipped += 1
            continue

        # Cada día de capturas cuenta como una sesión distinta para la validación cruzada.
        session = args.session or f"app-{path.stem.split('-')[0]}"
        target = destination / f"{session}__{path.stem}.wav"
        if target.exists():
            skipped += 1
            continue
        if not args.dry_run:
            shutil.copy2(path, target)
        print(f"  {path.name} → {target.relative_to(args.dataset)}")
        copied += 1

    action = "Se copiarían" if args.dry_run else "Copiadas"
    print(f"\n{action} {copied} capturas a {args.label} ({skipped} omitidas).")
    if copied and not args.dry_run:
        print("Siguiente paso: python ml/scripts/check_dataset.py y después ml/scripts/train.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
