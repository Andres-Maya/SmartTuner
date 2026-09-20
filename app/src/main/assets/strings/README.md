# Audios de las cuerdas

Aquí van los audios que suenan al pulsar el botón de una cuerda en la pantalla de afinación.
Si una cuerda no tiene audio, la app sintetiza una cuerda pulsada, así que el botón siempre
funciona: los audios solo lo mejoran.

## Nombres

Formatos admitidos: `wav`, `mp3`, `ogg`, `m4a`, `flac`.

La app prueba estos nombres en orden y usa el primero que encuentre:

| Nombre | Ejemplo | Para qué |
|---|---|---|
| `<instrumento>_<cuerda>.<ext>` | `guitar_6.wav` | La 6ª cuerda de la guitarra |
| `<instrumento>_<midi>.<ext>` | `guitar_40.wav` | La nota MIDI 40 (Mi2) en guitarra |
| `<midi>.<ext>` | `40.wav` | La nota MIDI 40 en cualquier instrumento |

Instrumentos: `guitar`, `bass`, `violin`, `viola`, `cello`, `ukulele`.

El número de cuerda es el estándar del instrumento: 1 es la más aguda. Ojo con el ukelele
soprano, que es re-entrante: su 4ª cuerda (Sol4) suena más aguda que la 3ª.

Las notas MIDI de cada afinación salen de `music/Instrument.kt`. Las de las afinaciones
estándar son:

| Instrumento | Cuerdas (1 → última) |
|---|---|
| Guitarra | 64 E4, 59 B3, 55 G3, 50 D3, 45 A2, 40 E2 |
| Bajo | 43 G2, 38 D2, 33 A1, 28 E1 |
| Violín | 76 E5, 69 A4, 62 D4, 55 G3 |
| Viola | 69 A4, 62 D4, 55 G3, 48 C3 |
| Violonchelo | 57 A3, 50 D3, 43 G2, 36 C2 |
| Ukelele | 69 A4, 64 E4, 60 C4, 67 G4 |

Las variantes (guitarra de 7 cuerdas, bajo de 5 y 6, Drop D, barítono…) añaden estas notas:
23 B0, 35 B1, 38 D2, 48 C3, 50 D3, 55 G3, 59 B3, 64 E4.

## Dos sitios donde ponerlos

1. **Aquí** (`app/src/main/assets/strings/`): viajan dentro de la app. Hay que recompilar.
2. **En el teléfono**, para probar sin recompilar:

   ```bash
   adb push mi_audio.wav /sdcard/Android/data/com.andres.smarttuner/files/strings/guitar_6.wav
   ```

   Esa carpeta manda sobre la de la app, así que sirve para probar un sonido encima del que
   ya viene incluido. Se borra al desinstalar.

## Consejos

- Que empiecen justo en el ataque, sin silencio al principio: el botón debe responder al instante.
- Entre 1 y 3 segundos es suficiente.
- Afínalos a La 440. La app no cambia el tono del audio al mover la referencia, solo lo reproduce
  (el afinador sí usa la referencia que tengas puesta).
- Mono y 44.1 kHz basta y pesa menos.
