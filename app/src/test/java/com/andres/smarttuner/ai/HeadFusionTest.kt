package com.andres.smarttuner.ai

import com.andres.smarttuner.music.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mezcla de la capa entrenada con grabaciones propias y la evidencia genérica de YAMNet. */
class HeadFusionTest {

    private val fusion = InstrumentFusion()

    private fun yamnetWindows(vararg scores: Pair<String, Float>) = List(4) { mapOf("Music" to 0.9f, *scores) }

    private fun headWindows(vararg probabilities: Pair<String, Float>) = List(4) { mapOf(*probabilities) }

    private fun bestOf(outcome: IdentificationOutcome): Instrument =
        (outcome as IdentificationOutcome.Identified).best.instrument

    @Test
    fun trainedHead_overridesYamnetConfusion() {
        // Caso real: YAMNet dice violín, la capa entrenada reconoce el violonchelo.
        val outcome = fusion.fuse(
            windows = yamnetWindows("Violin, fiddle" to 0.5f, "Bowed string instrument" to 0.4f),
            pitchesMidi = List(12) { 50f },
            headWindows = headWindows("cello" to 0.85f, "violin" to 0.1f, "guitar" to 0.03f, "bass" to 0.02f),
        )
        assertEquals(Instrument.CELLO, bestOf(outcome))
    }

    @Test
    fun yamnetStillContributes_forClassesTheHeadDoesNotKnow() {
        // La capa no se entrenó con viola: la evidencia de YAMNet debe mantenerla como opción.
        val outcome = fusion.fuse(
            windows = yamnetWindows("Violin, fiddle" to 0.6f, "Bowed string instrument" to 0.5f),
            pitchesMidi = List(12) { 48f }, // Do3: el violín no puede tocarlo, la viola sí
            headWindows = headWindows("violin" to 0.6f, "cello" to 0.3f, "guitar" to 0.1f),
        ) as IdentificationOutcome.Identified
        val viola = outcome.candidates.first { it.instrument == Instrument.VIOLA }
        assertTrue("La viola debería conservar probabilidad: $outcome", viola.probability > 0.05f)
    }

    @Test
    fun uncertainHead_doesNotOverrideYamnet() {
        // La capa entrenada duda entre todas las clases: debe mandar la evidencia de YAMNet.
        val outcome = fusion.fuse(
            windows = yamnetWindows("Ukulele" to 0.6f, "Plucked string instrument" to 0.5f),
            pitchesMidi = List(12) { 64f },
            headWindows = headWindows("guitar" to 0.26f, "cello" to 0.25f, "violin" to 0.25f, "ukulele" to 0.24f),
        )
        assertEquals(Instrument.UKULELE, bestOf(outcome))
    }

    @Test
    fun noise_isRejected_evenIfHeadIsConfident() {
        // Caso real: eructos y ruido de sala; YAMNet no oye música pero la capa entrenada
        // repartía el 71 % al ukelele porque su softmax siempre suma 1.
        val outcome = fusion.fuse(
            windows = List(4) { mapOf("Silence" to 0.15f, "Grunt" to 0.21f, "Burping, eructation" to 0.29f) },
            pitchesMidi = emptyList(),
            headWindows = headWindows("ukulele" to 0.71f, "other" to 0.18f, "bass" to 0.09f, "background" to 0.02f),
        )
        assertEquals(IdentificationOutcome.NoInstrument, outcome)
    }

    @Test
    fun musicWithoutClearInstrument_isStillIdentified() {
        // Guitarra real vista por YAMNet: Music alto pero puntuaciones de instrumento bajas.
        val outcome = fusion.fuse(
            windows = List(4) { mapOf("Music" to 0.5f, "Musical instrument" to 0.08f, "Guitar" to 0.04f) },
            pitchesMidi = List(12) { 45f },
            headWindows = headWindows("guitar" to 0.8f, "ukulele" to 0.1f, "background" to 0.05f),
        )
        assertEquals(Instrument.GUITAR, bestOf(outcome))
    }

    @Test
    fun backgroundClass_rejectsNonInstrumentAudio() {
        val outcome = fusion.fuse(
            windows = yamnetWindows("Speech" to 0.7f),
            pitchesMidi = emptyList(),
            headWindows = headWindows("background" to 0.9f, "guitar" to 0.05f, "cello" to 0.05f),
        )
        assertEquals(IdentificationOutcome.NoInstrument, outcome)
    }

    @Test
    fun withoutBackgroundClass_yamnetEvidenceStillRequired() {
        // La capa entrenada sin clase background no puede decidir sola que hay instrumento.
        val outcome = fusion.fuse(
            windows = List(4) { mapOf("Speech" to 0.95f) },
            pitchesMidi = emptyList(),
            headWindows = headWindows("guitar" to 0.5f, "cello" to 0.5f),
        )
        assertEquals(IdentificationOutcome.NoInstrument, outcome)
    }

    @Test
    fun registerStillFilters_impossibleNotes() {
        // La capa dice violín, pero suena un Do2 que el violín no puede producir.
        val outcome = fusion.fuse(
            windows = yamnetWindows("Violin, fiddle" to 0.4f, "Cello" to 0.3f),
            pitchesMidi = List(12) { 36f },
            headWindows = headWindows("violin" to 0.7f, "cello" to 0.3f),
        ) as IdentificationOutcome.Identified
        val violin = outcome.candidates.first { it.instrument == Instrument.VIOLIN }
        val cello = outcome.candidates.first { it.instrument == Instrument.CELLO }
        assertTrue("El registro debería castigar al violín: $outcome", cello.probability > violin.probability)
    }
}
