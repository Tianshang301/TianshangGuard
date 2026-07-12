package com.tianshang.guard.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import android.annotation.SuppressLint
import com.tianshang.guard.core.util.SecureLog
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class OnnxMlEngine(
    private val ortEnvironment: OrtEnvironment? = null,
    bpeTokenizer: BpeTokenizer? = null
) : MlEngine {

    internal val sessions = ConcurrentHashMap<ModelType, OrtSession>()
    internal val env: OrtEnvironment get() = ortEnvironment ?: OrtEnvironment.getEnvironment()
    private val urlPattern = Pattern.compile("https?://[^\\s]+")

    @SuppressLint("LogNotTimber")
    private val bpeTokenizer: BpeTokenizer? = bpeTokenizer?.also {
        if (it.isReady()) {
            SecureLog.i("OnnxMlEngine", "BPE tokenizer injected and ready")
        }
    }

    private fun encode(text: String, maxLength: Int = 512): LongArray {
        val bpe = bpeTokenizer
        return if (bpe != null && bpe.isReady()) {
            bpe.encode(text, maxLength)
        } else {
            ByteTokenizer.encode(text, maxLength)
        }
    }

    private fun normalizeUrlForModel(url: String): String {
        return url.removePrefix("www.")
    }

    private fun isBareDomain(url: String): Boolean {
        val domainOnly = url
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .split("/").first()
        val dotCount = domainOnly.count { c -> c == '.' }
        val schemeIdx = url.indexOf("://")
        val pathStart = if (schemeIdx >= 0) schemeIdx + 3 else 0
        return dotCount <= 1 && url.indexOf('/', pathStart) < 0
    }

    override fun loadModel(modelPath: String, type: ModelType) {
        val sessionOptions = OrtSession.SessionOptions().apply {
            try { addNnapi() } catch (_: Exception) {}
            setIntraOpNumThreads(2)
        }
        val newSession = env.createSession(modelPath, sessionOptions)
        val oldSession = sessions.put(type, newSession)
        oldSession?.close()
    }

    fun analyzeWithModel(text: String, type: ModelType): RiskLevel {
        val session = sessions[type] ?: return RiskLevel.SAFE
        val normalized = if (type == ModelType.URL) normalizeUrlForModel(text) else text
        val inputIds = encode(normalized)
        OnnxTensor.createTensor(env, arrayOf(inputIds)).use { inputTensor ->
            session.run(mapOf("input" to inputTensor)).use { outputs ->
                val score = (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f
                return RiskLevel.fromScore(score)
            }
        }
    }

    fun analyzeWithModelScore(text: String, type: ModelType): Float {
        val session = sessions[type] ?: return 0f
        val normalized = if (type == ModelType.URL) normalizeUrlForModel(text) else text
        val inputIds = encode(normalized)
        OnnxTensor.createTensor(env, arrayOf(inputIds)).use { inputTensor ->
            session.run(mapOf("input" to inputTensor)).use { outputs ->
                return (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f
            }
        }
    }

    override fun analyzeWebPage(text: String): RiskLevel {
        return analyzeWithModel(text, ModelType.URL)
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        val features = extractDomainFeatures(domain)
        return ruleBasedDomainAnalysis(features)
    }

    override fun analyzeSms(text: String): RiskLevel {
        val score = analyzeSmsScore(text)
        return RiskLevel.fromScore(score)
    }

    fun analyzeSmsScore(text: String): Float {
        val smsScore = analyzeWithModelScore(text, ModelType.SMS)
        val url = extractUrl(text)
        if (url == null) return smsScore

        var urlScore = analyzeWithModelScore(url, ModelType.URL)
        if (isBareDomain(url)) {
            urlScore *= 0.5f
        }
        return maxOf(smsScore, urlScore)
    }

    private fun extractUrl(text: String): String? {
        val matcher = urlPattern.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun extractDomainFeatures(domain: String): FloatArray {
        return floatArrayOf(
            domain.length.toFloat(),
            calculateEntropy(domain),
            domain.count { it.isDigit() }.toFloat(),
            if (hasHomograph(domain)) 1f else 0f
        )
    }

    private fun calculateEntropy(s: String): Float {
        if (s.isEmpty()) return 0f
        val freq = s.groupingBy { it }.eachCount()
        val len = s.length.toFloat()
        return -freq.values.sumOf { count ->
            val p = count / len
            p * kotlin.math.ln(p.toDouble())
        }.toFloat()
    }

    private fun hasHomograph(domain: String): Boolean {
        return domain.any { it in 'Ѐ'..'ӿ' }
    }

    private fun ruleBasedDomainAnalysis(features: FloatArray): RiskLevel {
        val length = features[0]
        if (length == 0f) return RiskLevel.SAFE
        val entropy = features[1]
        val digitRatio = features[2] / length
        val hasHomograph = features.getOrElse(3) { 0f } > 0f
        return when {
            hasHomograph -> RiskLevel.SUSPICIOUS
            entropy > 3.5f && digitRatio > 0.3f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun isModelLoaded(type: ModelType): Boolean = sessions.containsKey(type)

    /**
     * Close all ONNX sessions and release native resources.
     * Should be called when the engine is no longer needed.
     */
    fun close() {
        sessions.values.forEach { session ->
            try {
                session.close()
            } catch (e: Exception) {
                SecureLog.e("OnnxMlEngine", "Error closing session", e)
            }
        }
        sessions.clear()
        SecureLog.i("OnnxMlEngine", "All ONNX sessions closed")
    }
}
