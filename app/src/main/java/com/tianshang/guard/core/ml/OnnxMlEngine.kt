package com.tianshang.guard.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import android.content.Context
import com.tianshang.guard.core.util.SecureLog
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class OnnxMlEngine(private val context: Context) : MlEngine {

    private val sessions = ConcurrentHashMap<ModelType, OrtSession>()
    private val urlPattern = Pattern.compile("https?://[^\\s]+")
    
    // BPE tokenizer with fallback to byte tokenizer
    private val bpeTokenizer = BpeTokenizer(context)
    private var useBpe = false

    init {
        // Try to load BPE tokenizer
        useBpe = bpeTokenizer.load()
        if (useBpe) {
            SecureLog.i("OnnxMlEngine", "BPE tokenizer loaded successfully")
        } else {
            SecureLog.w("OnnxMlEngine", "BPE tokenizer not available, using byte tokenizer")
        }
    }

    private fun encode(text: String, maxLength: Int = 512): LongArray {
        return if (useBpe) {
            bpeTokenizer.encode(text, maxLength)
        } else {
            ByteTokenizer.encode(text, maxLength)
        }
    }

    override fun loadModel(modelPath: String, type: ModelType) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            try { addNnapi() } catch (_: Exception) {}
            setIntraOpNumThreads(2)
        }
        val newSession = env.createSession(modelPath, sessionOptions)
        // C-6: Close old session before replacing to prevent native memory leak
        val oldSession = sessions.put(type, newSession)
        oldSession?.close()
    }

    fun analyzeWithModel(text: String, type: ModelType): RiskLevel {
        val session = sessions[type] ?: return RiskLevel.SAFE
        val inputIds = encode(text)
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), arrayOf(inputIds)).use { inputTensor ->
            session.run(mapOf("input" to inputTensor)).use { outputs ->
                val score = (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f
                return RiskLevel.fromScore(score)
            }
        }
    }

    fun analyzeWithModelScore(text: String, type: ModelType): Float {
        val session = sessions[type] ?: return 0f
        val inputIds = encode(text)
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), arrayOf(inputIds)).use { inputTensor ->
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
        val textModelType = detectLanguage(text)
        val textScore = analyzeWithModelScore(text, textModelType)
        val smsScore = analyzeWithModelScore(text, ModelType.SMS)
        val url = extractUrl(text)
        val urlScore = if (url != null) analyzeWithModelScore(url, ModelType.URL) else 0f
        return maxOf(textScore, smsScore, urlScore)
    }

    private fun detectLanguage(text: String): ModelType {
        val hasChinese = text.any { it in '\u4E00'..'\u9FFF' }
        if (hasChinese) return ModelType.CHINESE
        return ModelType.ENGLISH
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
        return when {
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
