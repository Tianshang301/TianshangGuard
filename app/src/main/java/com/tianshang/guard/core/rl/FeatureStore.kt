package com.tianshang.guard.core.rl

import com.tianshang.guard.data.local.database.FeedbackDao
import com.tianshang.guard.data.local.database.FeedbackEntity
import com.tianshang.guard.data.local.database.FeedbackLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeatureStore(
    private val feedbackDao: FeedbackDao
) {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun storeFeatures(textHash: String, features: FeatureVector, label: FeedbackLabel) {
        ioScope.launch {
            val existing = feedbackDao.getByTextHash(textHash)
            if (existing != null) {
                // Update existing feedback with features
                val updated = existing.copy(features = features.toJson())
                feedbackDao.update(updated)
            }
        }
    }

    suspend fun getFeatureVectors(label: FeedbackLabel, limit: Int = 100): List<Pair<FeatureVector, FeedbackLabel>> {
        val feedbacks = when (label) {
            FeedbackLabel.PHISHING -> feedbackDao.getRecentByLabel(FeedbackLabel.PHISHING, limit)
            FeedbackLabel.FALSE_POSITIVE -> feedbackDao.getRecentByLabel(FeedbackLabel.FALSE_POSITIVE, limit)
        }
        return feedbacks.mapNotNull { fb ->
            val features = FeatureVector.fromJson(fb.features ?: return@mapNotNull null)
            features?.let { Pair(it, fb.label) }
        }
    }

    suspend fun getAllFeatureVectors(limit: Int = 200): List<Pair<FeatureVector, FeedbackLabel>> {
        val feedbacks = feedbackDao.getRecentFeedbackSync(limit)
        return feedbacks.mapNotNull { fb ->
            val features = FeatureVector.fromJson(fb.features ?: return@mapNotNull null)
            features?.let { Pair(it, fb.label) }
        }
    }
}
