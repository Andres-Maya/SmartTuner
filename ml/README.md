# Dataset y entrenamiento de la IA de SmartTuner

Aquí se guardan las grabaciones `.wav` para entrenar la IA que identifica instrumentos.
El modelo base (YAMNet) ya sabe escuchar; con tus grabaciones aprende a distinguir
**tus** instrumentos, grabados con **tu** micrófono.

Cómo funciona el entrenamiento: se pasa cada ventana de audio por el mismo `yamnet.tflite`
que la app lleva en `assets`, se toman sus **521 puntuaciones** como características y encima
se entrena una regresión logística multinomial (una capa lineal + softmax). El resultado es un
archivo de ~110 kB que la app aplica sin dependencias nuevas, y las características de
entrenamiento son idénticas a las que el teléfono calcula en tiempo real.

## 1. Estructura

```
ml/
├── dataset/
│   ├── guitar/       Guitarra (acústica, clásica o eléctrica)
│   ├── bass/         Bajo (eléctrico o contrabajo)
│   ├── violin/       Violín
│   ├── viola/        Viola
│   ├── cello/        Violonchelo
│   ├── ukulele/      Ukelele
│   ├── other/        Otros instrumentos: piano, voz cantada, flauta, batería…
│   └── background/   Sin instrumento: silencio, ruido de sala, gente hablando, clics
├── scripts/
│   ├── check_dataset.py       Revisa el dataset (funciona con tu Python normal)
│   ├── train.py               Entrena y exporta el modelo
│   └── make_smoke_dataset.py  Audio sintético para probar el flujo (no sirve para la IA real)
└── output/                    Resultados del último entrenamiento
```

Copia cada archivo en la carpeta de su instrumento. Los nombres de carpeta deben quedar exactamente así.

> `other` y `background` son importantes: sin ellas la IA siempre elegiría uno de los seis instrumentos,
> aunque estés hablando o tocando un piano. Sin `background`, la app sigue usando YAMNet para decidir
> si de verdad hay un instrumento sonando.

## 2. Cómo grabar

| Aspecto | Recomendación |
|---|---|
| Formato | `.wav` PCM de 16/24 bits o float, mono o estéreo |
| Frecuencia de muestreo | 44.1 kHz o 48 kHz (mínimo 16 kHz) |
| Duración | Clips de **2 a 30 segundos** (mínimo 1 s) |
| Cantidad mínima | **3 archivos por clase** (el entrenamiento necesita separar prueba y validación) |
| Cantidad recomendada | 30 clips y 5+ minutos por clase |
| Balance | Cantidad de audio parecida entre clases (máximo 3× de diferencia) |
| Micrófono | **El del teléfono**, a la distancia a la que se usa la app. Es el factor que más importa |

Qué tocar en cada clip, variando entre grabaciones:

- **Cuerdas al aire**, una por una (es lo que se hace al afinar).
- Notas sueltas en distintas partes del mástil, escalas y acordes.
- En violín, viola y violonchelo: **con arco y en pizzicato**, en las cuerdas graves y agudas.
  La cuerda **Do** de la viola y del violonchelo es lo que más ayuda a separarlas del violín.
- Fuerte y suave, cerca del teléfono y a ~1 m.
- Si puedes: distintos instrumentos, intérpretes, salas y teléfonos.

## 3. Nombres de archivo y sesiones

```
<sesión>__<descripción>.wav
```

Todo lo que va **antes de `__`** identifica la sesión de grabación (mismo día, sala e instrumento):

```
cello/
├── andres-casa-2026-09-14__cuerda-do.wav
├── andres-casa-2026-09-14__cuerda-sol.wav
└── conservatorio-sala2__escala-re.wav
```

El entrenamiento **nunca reparte una misma sesión** entre entrenamiento y prueba. Si lo hiciera,
la IA "reconocería la sala" y los resultados saldrían inflados. Si un archivo no lleva `__`,
cuenta como su propia sesión.

## 4. Revisar el dataset

Desde la raíz del proyecto, con tu Python normal:

```bash
python ml/scripts/check_dataset.py
```

Muestra archivos, minutos y sesiones por clase, y avisa de problemas: formato no soportado,
clips muy cortos, silencio, saturación, duplicados, clases desbalanceadas o con pocas sesiones.

## 5. Preparar el entorno de entrenamiento (una sola vez)

TensorFlow no soporta Python 3.14, así que se usa un entorno aparte con Python 3.12.
[uv](https://docs.astral.sh/uv/) lo descarga sin permisos de administrador:

```bash
pip install --user uv
uv venv ml/.venv --python 3.12
uv pip install --python ml/.venv/Scripts/python.exe tensorflow==2.20.0 scipy scikit-learn
```

## 6. Entrenar

```bash
ml/.venv/Scripts/python.exe ml/scripts/train.py
```

El script extrae las características y mide cada configuración con **validación cruzada por
sesiones** (hasta 5 pliegues): cada grabación se evalúa una vez, con un modelo que nunca la vio.
Así la cifra no depende de qué pocos archivos caigan en "prueba" por azar. Elige la mejor
configuración y entrena el modelo final con **todo** el audio. Deja en `ml/output/`:

| Archivo | Contenido |
|---|---|
| `instrument_head.json` | La capa final; se copia sola a `app/src/main/assets/` |
| `metrics.json` | Precisión de la validación cruzada, acierto por clase, matriz de confusión y archivos fallados |
| `folds/fold_N.json` | La capa de cada pliegue con sus archivos de prueba (la usa `simulate_app.py --cv`) |

Después hay que **recompilar la app** para que incluya el modelo nuevo. En el arranque de la
identificación, el log muestra `Modelo propio cargado: [...]` con las clases entrenadas.

Opciones útiles:

```bash
# entrenar sin copiar el resultado a la app
ml/.venv/Scripts/python.exe ml/scripts/train.py --no-install

# probar el flujo completo con audio sintético
python ml/scripts/make_smoke_dataset.py
ml/.venv/Scripts/python.exe ml/scripts/train.py --dataset ml/.smoke_dataset --no-install
```

## 7. Depurar sin el teléfono

`simulate_app.py` reproduce en el PC **toda** la decisión de la app (YIN + YAMNet + capa entrenada
+ fusión) sobre las grabaciones del dataset. Sirve para entender por qué se equivoca con un
instrumento sin tener que probar a mano en el teléfono:

```bash
# la cifra honesta: como la app (4 s) y cada archivo con un modelo que no lo vio
ml/.venv/Scripts/python.exe ml/scripts/simulate_app.py --cv --seconds 4

# con el modelo final (optimista: ya conoce todos los archivos)
ml/.venv/Scripts/python.exe ml/scripts/simulate_app.py --seconds 4

# detalle de una clase: alturas detectadas y probabilidades finales
ml/.venv/Scripts/python.exe ml/scripts/simulate_app.py --files cello --verbose
```

Compara dos cifras: **capa entrenada sola** y **app completa**. Si la app acierta menos que la capa,
el problema está en la fusión, no en el modelo.

## 8. Grabar con la propia app (recomendado)

La compilación de desarrollo guarda el audio de cada **Identificar instrumento** exactamente como
lo capta el teléfono, junto con los números que calculó (`captures/*.wav` y `*.json`). Es el
mejor material de entrenamiento, porque no hay diferencia entre lo que aprende el modelo y lo que
oye la app.

1. Toca **un solo instrumento** y pulsa *Identificar instrumento* varias veces
   (cuerdas al aire, notas sueltas, fuerte y suave). Anota la hora de inicio y fin.
2. Trae las capturas al PC:
   ```bash
   MSYS_NO_PATHCONV=1 adb pull /sdcard/Android/data/com.andres.smarttuner/files/captures ml/
   ```
3. Etiquétalas y cópialas al dataset (prueba antes con `--dry-run`):
   ```bash
   python ml/scripts/import_captures.py cello --since 20260916-1300 --until 20260916-1310
   ```
4. Repite con cada instrumento y con `background` (sin tocar nada), y reentrena.

La app guarda las últimas 60 capturas. Para comprobar que la app y el entrenamiento calculan lo
mismo con ese audio:

```bash
ml/.venv/Scripts/python.exe ml/scripts/compare_capture.py
```

La similitud "una ventana (A)" debe estar cerca de 1.0.

## 9. Cómo la usa la app

`InstrumentHead` aplica la capa entrenada a cada ventana y el resultado se mezcla así:

- **YAMNet decide si hay un instrumento.** Sin evidencia de música, la app responde "no reconocí
  un instrumento" aunque la capa entrenada esté segura: su softmax siempre reparte el 100 %.
- **Solo se usa la primera ventana de YAMNet de cada segundo.** MediaPipe devuelve además una
  ventana casi vacía con los 25 ms sobrantes; promediarla dejaba las puntuaciones a la mitad y la
  capa entrenada respondía "guitarra" casi siempre.
- **La capa entrenada decide cuál**, con un peso fijo del 70 %. Si el peso bajara cuando la capa
  duda, mandaría la parte genérica de YAMNet, que tiende a responder "guitarra".
- **El registro solo veta lo imposible** sobre la capa entrenada (un violín no puede dar un Do3).
  La bonificación por cuerdas al aire se aplica únicamente a la parte genérica de YAMNet: las notas
  pisadas de un instrumento coinciden con las cuerdas al aire de otro (Re4 de chelo = Re de viola)
  y daba la vuelta al resultado.
- **Si entrenaste la clase `other`**, esa manda sobre el "Otro" de YAMNet, que confunde las cuerdas
  frotadas con `Singing`.

Para ver qué decidió la app en el teléfono:

```bash
adb shell setprop log.tag.TunerViewModel DEBUG
adb logcat | grep TunerViewModel
```

Si borras `app/src/main/assets/instrument_head.json`, la app vuelve a funcionar solo con YAMNet.

## 10. Git

Los `.wav`, `ml/output/` y `ml/.venv/` están en `.gitignore`: el audio pesa mucho para un
repositorio normal. Guárdalos en Google Drive, un disco externo o usa
[Git LFS](https://git-lfs.com) si quieres versionarlos. El modelo entrenado
(`app/src/main/assets/instrument_head.json`) sí se versiona: son unos 110 kB.
