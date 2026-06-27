package com.tianshang.guard.core.alert

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

object AlertDataHolder {
    private const val TTL_MS = 60_000L // 60 seconds

    private val store = ConcurrentHashMap<String, AlertData>()
    private val handler = Handler(Looper.getMainLooper())
    private val pendingRunnables = ConcurrentHashMap<String, Runnable>()

    fun put(key: String, data: AlertData) {
        // Remove existing timeout for this key
        pendingRunnables.remove(key)?.let { handler.removeCallbacks(it) }
        store[key] = data
        val runnable = Runnable { store.remove(key) }
        pendingRunnables[key] = runnable
        handler.postDelayed(runnable, TTL_MS)
    }

    fun consume(key: String): AlertData? {
        return store.remove(key)
    }

    fun clear() {
        store.clear()
        handler.removeCallbacksAndMessages(null)
    }

    data class AlertData(
        val alertType: String,
        val domain: String? = null,
        val url: String? = null,
        val smsSender: String? = null,
        val smsBody: String? = null,
        val riskLevel: String? = null,
        val level: String? = null,
        val requireConfirm: Boolean = false,
        // Detection reasons (v1.3.x)
        val detectionReasons: List<String> = emptyList(),
        val mlScore: Float? = null,
        val features: Map<String, String> = emptyMap()
    )
}
