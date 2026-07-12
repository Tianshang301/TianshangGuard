package com.tianshang.guard.core.update

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.remote.GithubRulesApi
import com.tianshang.guard.data.remote.RulesDiff
import com.tianshang.guard.data.remote.RulesVersion
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class RuleUpdateInteractorTest : BaseUnitTest() {

    private val prefs = mockk<GuardPreferences>(relaxed = true)
    private val api = mockk<GithubRulesApi>(relaxed = true)
    private val domainDao = mockk<DomainDao>(relaxed = true)
    private val signatureVerifier = mockk<SignatureVerifier>(relaxed = true)
    private lateinit var interactor: RuleUpdateInteractor

    @Before
    fun setUp() {
        interactor = RuleUpdateInteractor(prefs, api, domainDao, signatureVerifier)
    }

    @Test
    fun `execute returns true when local version is latest`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("1.0.0")
        val result = interactor.execute()
        Assert.assertTrue(result)
        coVerify(exactly = 0) { api.getRulesDiff(any()) }
    }

    @Test
    fun `execute returns true on successful update`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val diff = RulesDiff(adds = listOf("new.com"), removes = listOf("old.com"), signature = "abc")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        every { signatureVerifier.verify(diff) } returns true
        coEvery { domainDao.isWhitelisted(any()) } returns false
        val result = interactor.execute()
        Assert.assertTrue(result)
        coVerify { prefs.setRulesVersion("2.0.0") }
    }

    @Test
    fun `execute returns false when signature fails`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val diff = RulesDiff(adds = listOf("new.com"), removes = emptyList(), signature = "bad")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        every { signatureVerifier.verify(diff) } returns false
        val result = interactor.execute()
        Assert.assertFalse(result)
    }

    @Test
    fun `execute returns false when diff exceeds max limit`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val hugeAdds = (1..10001).map { "domain$it.com" }
        val diff = RulesDiff(adds = hugeAdds, removes = emptyList(), signature = "abc")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        val result = interactor.execute()
        Assert.assertFalse(result)
    }

    @Test
    fun `execute returns false when api throws exception`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } throws RuntimeException("Network error")
        val result = interactor.execute()
        Assert.assertFalse(result)
    }

    @Test
    fun `execute inserts adds as blacklist entries`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val diff = RulesDiff(adds = listOf("evil.com", "phish.net"), removes = emptyList(), signature = "abc")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        every { signatureVerifier.verify(diff) } returns true
        coEvery { domainDao.isWhitelisted(any()) } returns false

        var insertCalled = false
        coEvery { domainDao.insert(any()) } answers {
            val entity = firstArg<DomainEntity>()
            insertCalled = true
            Assert.assertEquals(DomainCategory.BLACKLIST, entity.category)
            Assert.assertEquals("remote", entity.source)
        }

        interactor.execute()
        Assert.assertTrue(insertCalled)
    }

    @Test
    fun `execute skips adds that are already whitelisted`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val diff = RulesDiff(adds = listOf("trusted.com"), removes = emptyList(), signature = "abc")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        every { signatureVerifier.verify(diff) } returns true
        coEvery { domainDao.isWhitelisted("trusted.com") } returns true
        val result = interactor.execute()
        Assert.assertTrue(result)
        coVerify(exactly = 0) { domainDao.insert(any()) }
    }

    @Test
    fun `execute skips invalid domain formats`() = runTest {
        every { prefs.rulesVersion } returns flowOf("1.0.0")
        coEvery { api.getLatestRulesVersion() } returns RulesVersion("2.0.0")
        val diff = RulesDiff(adds = listOf("-invalid.com", "valid.com"), removes = emptyList(), signature = "abc")
        coEvery { api.getRulesDiff("1.0.0") } returns diff
        every { signatureVerifier.verify(diff) } returns true
        coEvery { domainDao.isWhitelisted(any()) } returns false

        var insertedCount = 0
        coEvery { domainDao.insert(any()) } answers { insertedCount++ }

        interactor.execute()
        Assert.assertEquals(1, insertedCount)
    }
}
