package com.tianshang.guard.domain

import com.tianshang.guard.data.local.GuardPreferences

class UpdateRulesUseCase(
    private val prefs: GuardPreferences
) {
    suspend fun execute(version: String) {
        prefs.setRulesVersion(version)
    }
}
