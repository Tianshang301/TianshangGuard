package com.tianshang.guard.core.alert

import com.tianshang.guard.BaseUnitTest
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CooldownManagerTest : BaseUnitTest() {

    private lateinit var cooldownManager: CooldownManager

    @Before
    fun setUp() {
        cooldownManager = CooldownManager(mockk(relaxed = true))
    }

    @Test
    fun `isInCooldown returns false for unknown key`() {
        Assert.assertFalse(cooldownManager.isInCooldown("unknown", 300))
    }

    @Test
    fun `isInCooldown returns true within cooldown period`() {
        cooldownManager.recordTrigger("test_key")
        Assert.assertTrue(cooldownManager.isInCooldown("test_key", 300))
    }

    @Test
    fun `isInCooldown returns false for zero cooldown`() {
        cooldownManager.recordTrigger("test_key")
        Assert.assertFalse(cooldownManager.isInCooldown("test_key", 0))
    }

    @Test
    fun `isInCooldown returns false for negative cooldown`() {
        cooldownManager.recordTrigger("test_key")
        Assert.assertFalse(cooldownManager.isInCooldown("test_key", -1))
    }

    @Test
    fun `multiple keys are tracked independently`() {
        cooldownManager.recordTrigger("key_a")
        cooldownManager.recordTrigger("key_b")
        Assert.assertTrue(cooldownManager.isInCooldown("key_a", 60))
        Assert.assertTrue(cooldownManager.isInCooldown("key_b", 60))
    }

    @Test
    fun `recordTrigger overwrites previous trigger`() {
        cooldownManager.recordTrigger("key")
        cooldownManager.recordTrigger("key")
        Assert.assertTrue(cooldownManager.isInCooldown("key", 300))
    }
}
