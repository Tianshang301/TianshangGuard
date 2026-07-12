package com.tianshang.guard.data.repository

import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleRepositoryTest {

    private lateinit var domainDao: DomainDao
    private lateinit var repository: RuleRepository

    @Before
    fun setUp() {
        domainDao = mockk(relaxed = true)
        repository = RuleRepository(domainDao)
    }

    @Test
    fun `isWhitelisted delegates to DAO`() = runTest {
        coEvery { domainDao.isWhitelisted("google.com") } returns true
        coEvery { domainDao.isWhitelisted("evil.com") } returns false

        assertTrue(repository.isWhitelisted("google.com"))
        assertFalse(repository.isWhitelisted("evil.com"))
        coVerify(exactly = 2) { domainDao.isWhitelisted(any()) }
    }

    @Test
    fun `isBlacklisted delegates to DAO`() = runTest {
        coEvery { domainDao.isBlacklisted("phishing.xyz") } returns true
        coEvery { domainDao.isBlacklisted("safe.com") } returns false

        assertTrue(repository.isBlacklisted("phishing.xyz"))
        assertFalse(repository.isBlacklisted("safe.com"))
    }

    @Test
    fun `getKnownDomains returns list from DAO`() = runTest {
        val domains = listOf("google.com", "phishing.xyz", "example.com")
        coEvery { domainDao.getKnownDomains() } returns domains

        val result = repository.getKnownDomains()
        assertEquals(3, result.size)
        assertTrue(result.contains("google.com"))
    }

    @Test
    fun `addToWhitelist inserts WHITELIST entity`() = runTest {
        repository.addToWhitelist("example.com")

        val slot = slot<DomainEntity>()
        coVerify { domainDao.insert(capture(slot)) }
        assertEquals("example.com", slot.captured.domain)
        assertEquals(DomainCategory.WHITELIST, slot.captured.category)
        assertEquals("user", slot.captured.source)
    }

    @Test
    fun `addToBlacklist inserts BLACKLIST entity`() = runTest {
        repository.addToBlacklist("bad-site.xyz")

        val slot = slot<DomainEntity>()
        coVerify { domainDao.insert(capture(slot)) }
        assertEquals("bad-site.xyz", slot.captured.domain)
        assertEquals(DomainCategory.BLACKLIST, slot.captured.category)
        assertEquals("user", slot.captured.source)
    }

    @Test
    fun `addSuspicious inserts SUSPICIOUS entity with confidence`() = runTest {
        repository.addSuspicious("suspicious-site.net", 0.85f)

        val slot = slot<DomainEntity>()
        coVerify { domainDao.insert(capture(slot)) }
        assertEquals("suspicious-site.net", slot.captured.domain)
        assertEquals(DomainCategory.SUSPICIOUS, slot.captured.category)
        assertEquals("detector", slot.captured.source)
        assertEquals(0.85f, slot.captured.confidence, 0.01f)
    }

    @Test
    fun `clearAll delegates to DAO`() = runTest {
        repository.clearAll()
        coVerify { domainDao.clearAll() }
    }

    @Test
    fun `getKnownDomains returns empty list when DAO returns empty`() = runTest {
        coEvery { domainDao.getKnownDomains() } returns emptyList()

        val result = repository.getKnownDomains()
        assertTrue(result.isEmpty())
    }
}
