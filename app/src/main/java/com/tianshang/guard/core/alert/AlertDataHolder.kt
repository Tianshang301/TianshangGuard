package com.tianshang.guard.core.alert

import java.util.concurrent.ConcurrentHashMap

object AlertDataHolder {
    private val store = ConcurrentHashMap<String, AlertData>()

    fun put(key: String, data: AlertData) {
        store[key] = data
    }

    fun consume(key: String): AlertData? {
        return store.remove(key)
    }

    data class AlertData(
        val alertType: String,
        val domain: String? = null,
        val url: String? = null,
        val smsSender: String? = null,
        val smsBody: String? = null,
        val riskLevel: String? = null,
        val level: String? = null,
        val requireConfirm: Boolean = false
    )
}
