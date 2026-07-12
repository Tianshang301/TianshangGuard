package com.tianshang.guard

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tianshang.guard.data.local.GuardPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuardPreferencesTest {

    private lateinit var prefs: GuardPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = GuardPreferences.create(context)
        runBlocking {
            prefs.setVpnAutoStart(true)
            prefs.setBootStart(true)
            prefs.setSmsMonitor(false)
        }
    }

    @Test
    fun onboardingDoneDefaultsToFalse() = runBlocking {
        assertFalse(prefs.onboardingDone.first())
    }

    @Test
    fun setOnboardingDoneStoresTrue() = runBlocking {
        prefs.setOnboardingDone()
        assertTrue(prefs.onboardingDone.first())
    }

    @Test
    fun vpnAutoStartDefaultsToTrue() = runBlocking {
        assertTrue(prefs.vpnAutoStart.first())
    }

    @Test
    fun smsMonitorDefaultsToFalse() = runBlocking {
        assertFalse(prefs.smsMonitor.first())
    }

    @Test
    fun setVpnAutoStartUpdatesValue() = runBlocking {
        prefs.setVpnAutoStart(false)
        assertFalse(prefs.vpnAutoStart.first())
    }

    @Test
    fun setRulesVersionUpdatesValue() = runBlocking {
        prefs.setRulesVersion("3.0.0")
        assertEquals("3.0.0", prefs.rulesVersion.first())
    }

    @Test
    fun isBootStartEnabledDefaultsToTrue() {
        assertTrue(prefs.isBootStartEnabled())
    }

    @Test
    fun setBootStartUpdatesValue() = runBlocking {
        prefs.setBootStart(false)
        assertFalse(prefs.isBootStartEnabled())
    }

    @Test
    fun setSmsMonitorUpdatesValue() = runBlocking {
        prefs.setSmsMonitor(true)
        assertTrue(prefs.smsMonitor.first())
    }
}
