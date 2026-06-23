package com.tianshang.guard.core.alert

import com.tianshang.guard.data.local.GuardPreferences

class CooldownManager(private val prefs: GuardPreferences) {
    private val triggerTimes = mutableMapOf<String, Long>()

    fun isInCooldown(key: String, cooldownSeconds: Int): Boolean {
        if (cooldownSeconds <= 0) return false
        val lastTrigger = triggerTimes[key] ?: return false
        return (System.currentTimeMillis() - lastTrigger) < cooldownSeconds * 1000
    }

    fun recordTrigger(key: String) {
        triggerTimes[key] = System.currentTimeMillis()
    }
}
