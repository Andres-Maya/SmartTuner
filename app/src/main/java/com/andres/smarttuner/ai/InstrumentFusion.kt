package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import kotlin.math.abs
import kotlin.math.pow

private const val MAX_LIKELY_OPTIONS = 3
private const val MIN_OPTION_PROBABILITY = 0.01f

data class InstrumentCandidate(
    val instrument: Instrument,
    /** Probabilidad relativa entre los candidatos (suman 1). */
    val probability: Float,
)

sealed interface IdentificationOutcome {
    data class Identified(
        /** Ordenados de mayor a menor probabilidad. */
        val candidates: List<InstrumentCandidate>,
        /** Etiqueta de AudioSet del instrumento "Otro" más probable, si hubo evidencia. */
        val otherLabel: String?,
    ) : IdentificationOutcome {
        val best: InstrumentCandidate get() = candidates.first()

        /**
         * Opciones para que el usuario elija: los más probables y, además, toda la familia del mejor
         * (violín, viola y violonchelo suenan parecido al micrófono y el modelo los confunde).
         */
        val options: List<InstrumentCandidate>
            get() {
                val family = best.instrument.family
                val likely = candidates.take(MAX_LIKELY_OPTIONS).filter { it.probability >= MIN_OPTION_PROBABILITY }
                val sameFamily = candidates.filter { family != null && it.instrument.family == family }
                return (listOf(best) + likely + sameFamily)
                    .distinctBy { it.instrument }
                    .sortedByDescending { it.probability }
            }
    }

    data object NoInstrument : IdentificationOutcome
}

/**
 * Combina dos fuentes de evidencia:
 *
 * 1. **Timbre** — puntuaciones de YAMNet promediadas en varias ventanas. Se agrupan por
 *    familia (arco / pulsada) y por instrumento concreto cuando el modelo lo conoce.
 * 2. **Registro** — alturas detectadas por YIN. Un instrumento que no puede producir una nota
 *    escuchada se penaliza (likelihood con tasa de error [pitchErrorRate]) y las notas que
 *    coinciden con sus cuerdas al aire lo refuerzan, algo muy común al afinar.
 *
 * La evidencia de familia se reparte entre sus instrumentos según timbre × registro,
 * lo que permite reconocer la viola aunque YAMNet no tenga esa clase.
 *
 * Si además existe una capa entrenada con grabaciones propias ([InstrumentHead]), su resultado
 * pesa [HEAD_WEIGHT] y lo anterior queda como respaldo para las clases que esa capa no conoce.
 */
class InstrumentFusion(
    private val minEvidence: Float = 0.05f,
    private val minPitchedFrames: Int = 8,
    private val pitchErrorRate: Float = 0.1f,
    private val familyShare: Float = 0.1f,
) {

    fun fuse(
        windows: List<Map<String, Float>>,
        pitchesMidi: List<Float>,
        headWindows: List<Map<String, Float>> = emptyList(),
    ): IdentificationOutcome {
        if (windows.isEmpty()) return IdentificationOutcome.NoInstrument

        val head = headWindows.meanByLabel()
        val headKnowsBackground = head.containsKey(InstrumentHead.BACKGROUND_LABEL)
        if (headKnowsBackground && head.getValue(InstrumentHead.BACKGROUND_LABEL) >= BACKGROUND_THRESHOLD) {
            return IdentificationOutcome.NoInstrument
        }

        val guitar = windows.meanOfMax(YamnetLabels.GUITAR)
        val bass = windows.meanOfMax(YamnetLabels.BASS)
        val violin = windows.meanOfMax(YamnetLabels.VIOLIN)
        val cello = windows.meanOfMax(YamnetLabels.CELLO)
        val ukulele = windows.meanOfMax(YamnetLabels.UKULELE)
        val bowedFamily = maxOf(windows.meanOfMax(YamnetLabels.BOWED_FAMILY), violin, cello)
        val pluckedFamily = maxOf(windows.meanOfMax(YamnetLabels.PLUCKED_FAMILY), guitar, bass, ukulele)

        val strongestOther = YamnetLabels.OTHER_INSTRUMENTS
            .map { label -> label to windows.meanOfMax(setOf(label)) }
            .maxByOrNull { it.second }
        val other = strongestOther?.second ?: 0f

        val registerWeight = { instrument: Instrument ->
            pitchLikelihood(instrument, pitchesMidi) * openStringBonus(instrument, pitchesMidi)
        }
        val scores = buildMap<Instrument, Float> {
            putAll(
                distribute(
                    familyScore = bowedFamily,
                    specific = mapOf(
                        Instrument.VIOLIN to violin,
                        // Timbre intermedio: el modelo suele confundir la viola con violín o chelo.
                        Instrument.VIOLA to (violin + cello) / 2f,
                        Instrument.CELLO to cello,
                    ),
                    registerWeight = registerWeight,
                ),
            )
            putAll(
                distribute(
                    familyScore = pluckedFamily,
                    specific = mapOf(
                        Instrument.GUITAR to guitar,
                        Instrument.BASS to bass,
                        Instrument.UKULELE to ukulele,
                    ),
                    registerWeight = registerWeight,
                ),
            )
            put(Instrument.OTHER, other)
        }

        // La capa propia solo puede descartar "no hay instrumento" si se entrenó con la clase
        // background; si no, sigue mandando la evidencia mínima de YAMNet.
        if (!headKnowsBackground && scores.values.max() < minEvidence) return IdentificationOutcome.NoInstrument

        val blended = blend(scores, head, registerWeight)
        val total = blended.values.sum()
        if (total <= 0f) return IdentificationOutcome.NoInstrument
        val candidates = blended
            .map { (instrument, score) -> InstrumentCandidate(instrument, score / total) }
            .sortedByDescending { it.probability }
        return IdentificationOutcome.Identified(
            candidates = candidates,
            otherLabel = strongestOther?.first?.takeIf { other >= minEvidence },
        )
    }

    /** exp(media de log p): 1 si todas las notas caben en el rango, cae rápido con notas imposibles. */
    internal fun pitchLikelihood(instrument: Instrument, pitchesMidi: List<Float>): Float {
        if (pitchesMidi.size < minPitchedFrames) return 1f
        val outOfRange = pitchesMidi.count { !instrument.canPlay(it) }.toFloat() / pitchesMidi.size
        return pitchErrorRate.pow(outOfRange)
    }

    /** Entre 1 y 2 según la fracción de notas que coinciden con cuerdas al aire. */
    internal fun openStringBonus(instrument: Instrument, pitchesMidi: List<Float>): Float {
        if (pitchesMidi.size < minPitchedFrames || instrument.isChromatic) return 1f
        val nearOpenString = pitchesMidi.count { pitch ->
            instrument.strings.any { abs(pitch - it.midi) <= OPEN_STRING_TOLERANCE }
        }.toFloat() / pitchesMidi.size
        return 1f + nearOpenString
    }

    /** Mezcla la capa entrenada (timbre aprendido) con la evidencia genérica de YAMNet + registro. */
    private fun blend(
        fusionScores: Map<Instrument, Float>,
        head: Map<String, Float>,
        registerWeight: (Instrument) -> Float,
    ): Map<Instrument, Float> {
        if (head.isEmpty()) return fusionScores
        val learned = fusionScores.keys.associateWith { (head[it.datasetLabel] ?: 0f) * registerWeight(it) }
        val learnedTotal = learned.values.sum()
        if (learnedTotal <= 0f) return fusionScores

        val genericTotal = fusionScores.values.sum()
        return fusionScores.keys.associateWith { instrument ->
            val fromHead = learned.getValue(instrument) / learnedTotal
            val fromYamnet = if (genericTotal > 0f) fusionScores.getValue(instrument) / genericTotal else 0f
            HEAD_WEIGHT * fromHead + (1f - HEAD_WEIGHT) * fromYamnet
        }
    }

    private fun List<Map<String, Float>>.meanByLabel(): Map<String, Float> {
        if (isEmpty()) return emptyMap()
        val totals = HashMap<String, Float>()
        forEach { window -> window.forEach { (label, value) -> totals[label] = (totals[label] ?: 0f) + value } }
        return totals.mapValues { it.value / size }
    }

    private fun distribute(
        familyScore: Float,
        specific: Map<Instrument, Float>,
        registerWeight: (Instrument) -> Float,
    ): Map<Instrument, Float> {
        val weights = specific.mapValues { (instrument, score) ->
            (score + familyShare * familyScore) * registerWeight(instrument)
        }
        val sum = weights.values.sum()
        return weights.mapValues { (_, weight) -> if (sum > 0f) familyScore * weight / sum else 0f }
    }

    private fun List<Map<String, Float>>.meanOfMax(labels: Set<String>): Float =
        sumOf { window -> labels.maxOf { window[it] ?: 0f }.toDouble() }.toFloat() / size

    private companion object {
        const val OPEN_STRING_TOLERANCE = 0.3f // 30 cents
        const val HEAD_WEIGHT = 0.75f
        const val BACKGROUND_THRESHOLD = 0.6f
    }
}
