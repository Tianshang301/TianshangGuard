package com.tianshang.guard.core.ml

enum class ModelType {
    URL,
    ENGLISH,
    SMS
}

enum class RiskLevel(val threshold: Float) {
    SAFE(0.30f),
    SUSPICIOUS(0.59f),
    DANGEROUS(1.0f);

    fun toScore(): Float = when (this) {
        SAFE -> 0.15f
        SUSPICIOUS -> 0.445f
        DANGEROUS -> 0.795f
    }

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
