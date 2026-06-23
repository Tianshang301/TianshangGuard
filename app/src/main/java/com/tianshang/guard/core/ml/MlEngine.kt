package com.tianshang.guard.core.ml

enum class ModelType {
    URL,
    CHINESE,
    ENGLISH,
    JAPANESE
}

enum class RiskLevel(val threshold: Float) {
    SAFE(0.3f),
    SUSPICIOUS(0.7f),
    DANGEROUS(1.0f);

    companion object {
        fun fromScore(score: Float): RiskLevel = when {
            score < SAFE.threshold -> SAFE
            score < SUSPICIOUS.threshold -> SUSPICIOUS
            else -> DANGEROUS
        }
    }
}

sealed class MlState {
    object Loading : MlState()
    object Ready : MlState()
    data class Failed(val reason: String) : MlState()
    object Fallback : MlState()
}

interface MlEngine {
    fun analyzeWebPage(text: String): RiskLevel
    fun analyzeDomain(domain: String): RiskLevel
    fun analyzeSms(text: String): RiskLevel
    fun loadModel(modelPath: String, type: ModelType = ModelType.URL)
    fun isModelLoaded(type: ModelType = ModelType.URL): Boolean
}
