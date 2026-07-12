package com.tianshang.guard.core.ml

enum class ModelType {
    URL,
    ENGLISH,
    SMS
}

enum class RiskLevel(val threshold: Float) {
    SAFE(0.50f),
    SUSPICIOUS(0.90f),
    DANGEROUS(1.0f);

    // Convert discrete risk level to continuous score (midpoint of range)
    fun toScore(): Float = when (this) {
        SAFE -> 0.25f
        SUSPICIOUS -> 0.70f
        DANGEROUS -> 0.95f
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
