package com.tianshang.guard.core.alert

import com.tianshang.guard.data.local.GuardPreferences
import java.util.concurrent.ConcurrentHashMap

class CooldownManager(private val prefs: GuardPreferences) {
    // BUGFIX: Use ConcurrentHashMap for thread safety (VPN thread + SMS receiver concurrent access)
    private val triggerTimes = ConcurrentHashMap<String, Long>()
    // M-15: Maximum age for trigger entries (10 minutes)
    private val maxEntryAgeMs = 600_000L

    fun isInCooldown(key: String, cooldownSeconds: Int): Boolean {
        if (cooldownSeconds <= 0) return false
        // M-15: Cleanup old entries periodically
        cleanupOldEntries()
        val lastTrigger = triggerTimes[key] ?: return false
        // L-1: Use toLong() to prevent integer overflow
        return (System.currentTimeMillis() - lastTrigger) < cooldownSeconds.toLong() * 1000
    }

    fun recordTrigger(key: String) {
        triggerTimes[key] = System.currentTimeMillis()
    }

    private fun cleanupOldEntries() {
        val cutoff = System.currentTimeMillis() - maxEntryAgeMs
        triggerTimes.entries.removeIf { it.value < cutoff }
    }
}
