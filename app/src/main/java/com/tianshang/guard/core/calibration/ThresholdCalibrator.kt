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

    // Momentum decay factor
    private val momentum = 0.9f
    private val learningRate = 0.1f

    // Default thresholds
    private val defaultSuspicious = 0.10f
    private val defaultDangerous = 0.50f

    // Calibration interval
    private val calibrationInterval = 50

    init {
        // Load saved biases or initialize to 0
        if (!prefs.contains(KEY_SUSPICIOUS_BIAS)) {
            prefs.edit().putFloat(KEY_SUSPICIOUS_BIAS, 0f).apply()
        }
        if (!prefs.contains(KEY_DANGEROUS_BIAS)) {
            prefs.edit().putFloat(KEY_DANGEROUS_BIAS, 0f).apply()
        }
    }

    fun getSuspiciousThreshold(): Float {
        return defaultSuspicious + prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)
    }

    fun getDangerousThreshold(): Float {
        return defaultDangerous + prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)
    }

    fun recordFeedback(modelScore: Float, label: FeedbackLabel) {
        ioScope.launch {
            val currentDangerousBias = prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)
            val currentSuspiciousBias = prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)

            when (label) {
                FeedbackLabel.PHISHING -> {
                    // User confirmed phishing: if model score was low, lower dangerous threshold
                    if (modelScore < defaultDangerous) {
                        val delta = (modelScore - defaultDangerous) * learningRate
                        val newBias = momentum * currentDangerousBias + (1 - momentum) * delta
                        prefs.edit().putFloat(KEY_DANGEROUS_BIAS, newBias).apply()
                    }
                }
                FeedbackLabel.FALSE_POSITIVE -> {
                    // User marked as false positive: if model score was high, raise suspicious threshold
                    if (modelScore > defaultSuspicious) {
                        val delta = (modelScore - defaultSuspicious) * learningRate
                        val newBias = momentum * currentSuspiciousBias + (1 - momentum) * delta
                        prefs.edit().putFloat(KEY_SUSPICIOUS_BIAS, newBias).apply()
                    }
                }
            }
        }
    }

    fun reset() {
        prefs.edit()
            .putFloat(KEY_SUSPICIOUS_BIAS, 0f)
            .putFloat(KEY_DANGEROUS_BIAS, 0f)
            .apply()
    }

    fun getSuspiciousBias(): Float = prefs.getFloat(KEY_SUSPICIOUS_BIAS, 0f)

    fun getDangerousBias(): Float = prefs.getFloat(KEY_DANGEROUS_BIAS, 0f)

    companion object {
        private const val KEY_SUSPICIOUS_BIAS = "suspicious_bias"
        private const val KEY_DANGEROUS_BIAS = "dangerous_bias"
    }
}
