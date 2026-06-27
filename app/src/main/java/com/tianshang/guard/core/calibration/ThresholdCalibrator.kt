package com.tianshang.guard.core.calibration

import android.content.Context
import android.content.SharedPreferences
import com.tianshang.guard.data.local.database.FeedbackDao
import com.tianshang.guard.data.local.database.FeedbackLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThresholdCalibrator(
    private val context: Context,
    private val feedbackDao: FeedbackDao
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("threshold_calibration", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val momentum = 0.9f
    private val learningRate = 0.1f

    private val defaultSuspicious = 0.10f
    private val defaultDangerous = 0.50f

    // Upper bound protection: bias cannot exceed these values
    private val maxSuspiciousBias = 0.30f
    private val maxDangerousBias = 0.20f

    // Decay: bias decays toward 0 every N feedbacks
    private val decayInterval = 10
    private val decayFactor = 0.95f

    init {
        if (!prefs.contains(KEY_SUSPICIOUS_BIAS)) {
            prefs.edit().putFloat(KEY_SUSPICIOUS_BIAS, 0f).apply()
        }
        if (!prefs.contains(KEY_DANGEROUS_BIAS)) {
            prefs.edit().putFloat(KEY_DANGEROUS_BIAS, 0f).apply()
        }
        if (!prefs.contains(KEY_FEEDBACK_COUNT)) {
            prefs.edit().putInt(KEY_FEEDBACK_COUNT, 0).apply()
        }
    }

    fun getSuspiciousThreshold(): Float {
        return (defaultSuspicious + prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)).coerceIn(defaultSuspicious, defaultSuspicious + maxSuspiciousBias)
    }

    fun getDangerousThreshold(): Float {
        // BUGFIX: Allow threshold to go below default (for anti-fraud: FNR > FPR priority)
        return (defaultDangerous + prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)).coerceIn(0.30f, defaultDangerous + maxDangerousBias)
    }

    fun recordFeedback(modelScore: Float, label: FeedbackLabel) {
        ioScope.launch {
            val count = prefs.getInt(KEY_FEEDBACK_COUNT, 0) + 1
            prefs.edit().putInt(KEY_FEEDBACK_COUNT, count).apply()

            // Apply decay every N feedbacks
            if (count % decayInterval == 0) {
                applyDecay()
            }

            val currentDangerousBias = prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)
            val currentSuspiciousBias = prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)

            when (label) {
                FeedbackLabel.PHISHING -> {
                    if (modelScore < defaultDangerous) {
                        val delta = (modelScore - defaultDangerous) * learningRate
                        val newBias = (momentum * currentDangerousBias + (1 - momentum) * delta)
                            .coerceIn(-maxDangerousBias, maxDangerousBias)
                        prefs.edit().putFloat(KEY_DANGEROUS_BIAS, newBias).apply()
                    }
                }
                FeedbackLabel.FALSE_POSITIVE -> {
                    if (modelScore > defaultSuspicious) {
                        val delta = (modelScore - defaultSuspicious) * learningRate
                        val newBias = (momentum * currentSuspiciousBias + (1 - momentum) * delta)
                            .coerceIn(0f, maxSuspiciousBias)
                        prefs.edit().putFloat(KEY_SUSPICIOUS_BIAS, newBias).apply()
                    }
                }
            }
        }
    }

    private fun applyDecay() {
        val suspiciousBias = prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)
        val dangerousBias = prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)
        prefs.edit()
            .putFloat(KEY_SUSPICIOUS_BIAS, suspiciousBias * decayFactor)
            .putFloat(KEY_DANGEROUS_BIAS, dangerousBias * decayFactor)
            .apply()
    }

    fun reset() {
        prefs.edit()
            .putFloat(KEY_SUSPICIOUS_BIAS, 0f)
            .putFloat(KEY_DANGEROUS_BIAS, 0f)
            .putInt(KEY_FEEDBACK_COUNT, 0)
            .apply()
    }

    fun getSuspiciousBias(): Float = prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)
    fun getDangerousBias(): Float = prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)

    companion object {
        private const val KEY_SUSPICIOUS_BIAS = "suspicious_bias"
        private const val KEY_DANGEROUS_BIAS = "dangerous_bias"
        private const val KEY_FEEDBACK_COUNT = "feedback_count"
    }
}
