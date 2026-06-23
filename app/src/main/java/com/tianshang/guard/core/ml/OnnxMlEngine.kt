package com.tianshang.guard.core.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor

class BertTokenizer {

    fun encode(text: String, maxLength: Int): LongArray {
        val tokens = LongArray(maxLength) { 0L }
        tokens[0] = 101L
        val textBytes = text.take(maxLength - 2).encodeToByteArray()
        for (i in textBytes.indices) {
            tokens[i + 1] = textBytes[i].toLong() and 0xFF
        }
        tokens[minOf(textBytes.size + 1, maxLength - 1)] = 102L
        return tokens
    }
}

class OnnxMlEngine : MlEngine {

    private val sessions = mutableMapOf<ModelType, OrtSession>()
    private val tokenizer = BertTokenizer()

    override fun loadModel(modelPath: String, type: ModelType) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            addNnapi()
            setIntraOpNumThreads(2)
        }
        sessions[type] = env.createSession(modelPath, sessionOptions)
    }

    fun analyzeWithModel(text: String, type: ModelType): RiskLevel {
        val session = sessions[type] ?: return RiskLevel.SAFE
        val inputIds = tokenizer.encode(text, maxLength = 512)
        val inputTensor = OnnxTensor.createTensor(
            OrtEnvironment.getEnvironment(),
            arrayOf(inputIds)
        )

        val outputs = session.run(mapOf("input" to inputTensor))
        val score = (outputs?.get(0)?.value as? FloatArray)?.firstOrNull() ?: 0f

        return RiskLevel.fromScore(score)
    }

    override fun analyzeWebPage(text: String): RiskLevel {
        return analyzeWithModel(text, ModelType.URL)
    }

    override fun analyzeDomain(domain: String): RiskLevel {
        val features = extractDomainFeatures(domain)
        return ruleBasedDomainAnalysis(features)
    }

    override fun analyzeSms(text: String): RiskLevel {
        return analyzeWithModel(text, ModelType.CHINESE)
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
        val entropy = features[1]
        val digitRatio = features[2] / features[0]
        return when {
            entropy > 3.5f && digitRatio > 0.3f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }
    }

    override fun isModelLoaded(type: ModelType): Boolean = sessions.containsKey(type)
}
