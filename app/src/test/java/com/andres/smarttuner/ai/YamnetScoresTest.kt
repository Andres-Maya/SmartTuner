package com.andres.smarttuner.ai

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.ClassificationResult
import com.google.mediapipe.tasks.components.containers.Classifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Optional

class YamnetScoresTest {

    private fun window(vararg scores: Pair<Int, Float>): ClassificationResult = ClassificationResult.create(
        listOf(
            Classifications.create(
                scores.map { (index, score) -> Category.create(score, index, "label$index", "") },
                0,
                Optional.empty(),
            ),
        ),
        Optional.empty(),
    )

    @Test
    fun usesOnlyTheFirstWindow_notThePaddedRemainder() {
        // Caso real: MediaPipe parte 1 s en una ventana completa y otra casi vacía.
        val full = window(0 to 0.92f, 494 to 0.01f)
        val paddedRemainder = window(0 to 0.0f, 494 to 0.85f)

        val scores = firstWindowScores(listOf(full, paddedRemainder))

        assertEquals(0.92f, scores.byIndex[0], 1e-6f)
        assertEquals(0.01f, scores.byIndex[494], 1e-6f)
        assertEquals(0.92f, scores.byLabel.getValue("label0"), 1e-6f)
        assertEquals(2, scores.resultCount)
        assertEquals(521, scores.byIndex.size)
    }

    @Test
    fun noResults_areEmpty() {
        val scores = firstWindowScores(emptyList())
        assertTrue(scores.byIndex.isEmpty())
        assertTrue(scores.byLabel.isEmpty())
        assertEquals(0, scores.resultCount)
    }

    @Test
    fun outOfRangeIndices_areIgnored() {
        val scores = firstWindowScores(listOf(window(600 to 0.5f, 3 to 0.2f)))
        assertEquals(0.2f, scores.byIndex[3], 1e-6f)
        assertEquals(0.5f, scores.byLabel.getValue("label600"), 1e-6f)
    }
}
