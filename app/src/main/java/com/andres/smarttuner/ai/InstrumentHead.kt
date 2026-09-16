package com.andres.smarttuner.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Capa lineal entrenada con grabaciones propias sobre las 521 puntuaciones de YAMNet
 * (ver ml/README.md). Es opcional: si el archivo no está en assets, la app usa solo YAMNet.
 */
class InstrumentHead internal constructor(
    val labels: List<String>,
    private val weights: Array<FloatArray>,
    private val bias: FloatArray,
    private val logFeatures: Boolean,
    /** Normalización aprendida en el entrenamiento (vacía en modelos versión 1). */
    private val mean: FloatArray = FloatArray(0),
    private val scale: FloatArray = FloatArray(0),
) {

    /** Probabilidad por clase entrenada para una ventana de audio ya clasificada por YAMNet. */
    fun probabilities(scoresByIndex: FloatArray): Map<String, Float> {
        val columns = min(weights.firstOrNull()?.size ?: 0, scoresByIndex.size)
        val normalized = FloatArray(columns) { feature(it, scoresByIndex[it]) }
        val logits = FloatArray(labels.size) { label ->
            val row = weights[label]
            var sum = bias[label]
            for (index in 0 until columns) sum += row[index] * normalized[index]
            sum
        }
        val highest = logits.max()
        val exponentials = FloatArray(logits.size) { exp(logits[it] - highest) }
        val total = exponentials.sum()
        return labels.indices.associate { labels[it] to exponentials[it] / total }
    }

    private fun feature(index: Int, score: Float): Float {
        val value = if (logFeatures) ln(score + 1e-6f) else score
        if (index >= mean.size || index >= scale.size) return value
        val deviation = scale[index]
        return if (deviation > 0f) (value - mean[index]) / deviation else value - mean[index]
    }

    companion object {
        const val ASSET = "instrument_head.json"
        const val BACKGROUND_LABEL = "background"

        /** `null` cuando todavía no se ha entrenado ningún modelo propio. */
        fun loadFromAssets(context: Context, asset: String = ASSET): InstrumentHead? = try {
            if (context.assets.list("")?.contains(asset) != true) {
                null
            } else {
                parse(context.assets.open(asset).bufferedReader().use { it.readText() })
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar $asset", e)
            null
        }

        fun parse(json: String): InstrumentHead {
            val root = JSONObject(json)
            val labelsJson = root.getJSONArray("labels")
            val labels = List(labelsJson.length()) { labelsJson.getString(it) }
            val weightsJson = root.getJSONArray("weights")
            val weights = Array(weightsJson.length()) { row ->
                val values = weightsJson.getJSONArray(row)
                FloatArray(values.length()) { values.getDouble(it).toFloat() }
            }
            val biasJson = root.getJSONArray("bias")
            val bias = FloatArray(biasJson.length()) { biasJson.getDouble(it).toFloat() }
            require(labels.isNotEmpty() && labels.size == weights.size && labels.size == bias.size) {
                "instrument_head.json inconsistente: ${labels.size} clases, ${weights.size} filas, ${bias.size} sesgos"
            }
            return InstrumentHead(
                labels = labels,
                weights = weights,
                bias = bias,
                logFeatures = root.optString("featureTransform") == "log",
                mean = root.optJSONArray("mean").toFloatArray(),
                scale = root.optJSONArray("scale").toFloatArray(),
            )
        }

        private fun JSONArray?.toFloatArray(): FloatArray =
            if (this == null) FloatArray(0) else FloatArray(length()) { getDouble(it).toFloat() }

        private const val TAG = "InstrumentHead"
    }
}
