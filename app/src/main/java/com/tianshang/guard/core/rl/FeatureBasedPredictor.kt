package com.tianshang.guard.core.rl

import com.tianshang.guard.core.ml.RiskLevel
import com.tianshang.guard.data.local.database.FeedbackLabel

class FeatureBasedPredictor(
    private val featureStore: FeatureStore
) {

    suspend fun predict(features: FeatureVector): FeaturePrediction {
        val allVectors = featureStore.getAllFeatureVectors(limit = 200)
        if (allVectors.isEmpty()) return FeaturePrediction(RiskLevel.SAFE, 0f, 0)

        val featureArray = features.toFloatArray()

        // Calculate cosine similarity with all stored feature vectors
        val similarities = allVectors.map { (storedFeatures, label) ->
            val storedArray = storedFeatures.toFloatArray()
            val similarity = cosineSimilarity(featureArray, storedArray)
            Pair(similarity, label)
        }

        // Get top-K most similar
        val topK = similarities.sortedByDescending { it.first }.take(10)
        if (topK.isEmpty()) return FeaturePrediction(RiskLevel.SAFE, 0f, 0)

        // Calculate phishing ratio in top-K
        val phishingCount = topK.count { it.second == FeedbackLabel.PHISHING }
        val matchCount = topK.size
        val phishingRatio = phishingCount.toFloat() / matchCount

        // Convert to risk level
        val riskLevel = when {
            phishingRatio >= 0.7f -> RiskLevel.DANGEROUS
            phishingRatio >= 0.4f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.SAFE
        }

        return FeaturePrediction(riskLevel, phishingRatio, matchCount)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0f || normB == 0f) return 0f

        return dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    data class FeaturePrediction(
        val riskLevel: RiskLevel,
        val phishingRatio: Float,
        val matchCount: Int
    )
}
