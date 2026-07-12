package com.tianshang.guard.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "guard_preferences")

class GuardPreferences(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_ONBOARDING_DONE = intPreferencesKey("onboarding_done")
        private val KEY_SUSPICIOUS_COOLDOWN = intPreferencesKey("suspicious_cooldown_seconds")
        private val KEY_RULES_VERSION = stringPreferencesKey("rules_version")
        private val KEY_VPN_AUTO_START = booleanPreferencesKey("vpn_auto_start")
        private val KEY_BEHAVIOR_MONITOR = booleanPreferencesKey("behavior_monitor")
        private val KEY_BOOT_START = booleanPreferencesKey("boot_start")
        private val KEY_SOUND_ALERT = booleanPreferencesKey("sound_alert")
        private val KEY_VIBRATE_ALERT = booleanPreferencesKey("vibrate_alert")
        private val KEY_SMS_MONITOR = booleanPreferencesKey("sms_monitor")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")

        fun create(context: Context): GuardPreferences {
            return GuardPreferences(context.dataStore)
        }
    }

    val suspiciousCooldownSeconds: Int
        get() = 300

    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] == 1
    }

    val vpnAutoStart: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VPN_AUTO_START] ?: true
    }

    val behaviorMonitor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BEHAVIOR_MONITOR] ?: true
    }

    val bootStart: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BOOT_START] ?: true
    }

    val soundAlert: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SOUND_ALERT] ?: true
    }

    val vibrateAlert: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VIBRATE_ALERT] ?: true
    }

    val smsMonitor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_MONITOR] ?: false
    }

    val language: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "system"
    }

    val rulesVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_RULES_VERSION] ?: "0"
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = 1 }
    }

    suspend fun setRulesVersion(version: String) {
        dataStore.edit { prefs -> prefs[KEY_RULES_VERSION] = version }
    }

    fun isBootStartEnabled(): Boolean {
        return kotlinx.coroutines.runBlocking {
            dataStore.data.map { prefs -> prefs[KEY_BOOT_START] ?: true }.first()
        }
    }

    suspend fun setVpnAutoStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VPN_AUTO_START] = enabled }
    }

    suspend fun setBehaviorMonitor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_BEHAVIOR_MONITOR] = enabled }
    }

    suspend fun setBootStart(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_BOOT_START] = enabled }
    }

    suspend fun setSoundAlert(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SOUND_ALERT] = enabled }
    }

    suspend fun setVibrateAlert(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VIBRATE_ALERT] = enabled }
    }

    suspend fun setSmsMonitor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SMS_MONITOR] = enabled }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = language }
    }
}
