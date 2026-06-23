package com.tianshang.guard.core.alert

import com.tianshang.guard.data.local.GuardPreferences
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CooldownManagerTest {

    private lateinit var cooldownManager: CooldownManager

    @Before
    fun setUp() {
        cooldownManager = CooldownManager(mockk<GuardPreferences>(relaxed = true))
    }

    @Test
    fun `no trigger recorded returns false`() {
        assertFalse(cooldownManager.isInCooldown("test-key", 60))
    }

    @Test
    fun `cooldownSeconds zero disables cooldown`() {
        cooldownManager.recordTrigger("test-key")
        assertFalse(cooldownManager.isInCooldown("test-key", 0))
    }

    @Test
    fun `cooldownSeconds negative disables cooldown`() {
        cooldownManager.recordTrigger("test-key")
        assertFalse(cooldownManager.isInCooldown("test-key", -1))
    }

    @Test
    fun `recently recorded trigger returns true`() {
        cooldownManager.recordTrigger("test-key")
        assertTrue(cooldownManager.isInCooldown("test-key", 60))
    }

    @Test
    fun `different keys have independent cooldowns`() {
        cooldownManager.recordTrigger("key-1")
        assertTrue(cooldownManager.isInCooldown("key-1", 60))
        assertFalse(cooldownManager.isInCooldown("key-2", 60))
    }

    @Test
    fun `recooldown resets timer`() {
        cooldownManager.recordTrigger("test-key")
        assertTrue(cooldownManager.isInCooldown("test-key", 60))
        cooldownManager.recordTrigger("test-key")
        assertTrue(cooldownManager.isInCooldown("test-key", 60))
    }
}
