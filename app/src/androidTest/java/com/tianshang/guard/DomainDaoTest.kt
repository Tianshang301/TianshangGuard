package com.tianshang.guard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.local.database.GuardDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomainDaoTest {

    private lateinit var db: GuardDatabase
    private lateinit var dao: DomainDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.domainDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryWhitelist() = runBlocking {
        dao.insert(
            DomainEntity(
                domain = "example.com",
                category = DomainCategory.WHITELIST,
                source = "test"
            )
        )
        assertTrue(dao.isWhitelisted("example.com"))
        assertFalse(dao.isBlacklisted("example.com"))
    }

    @Test
    fun insertAndQueryBlacklist() = runBlocking {
        dao.insert(
            DomainEntity(
                domain = "phishing.xyz",
                category = DomainCategory.BLACKLIST,
                source = "test"
            )
        )
        assertTrue(dao.isBlacklisted("phishing.xyz"))
        assertFalse(dao.isWhitelisted("phishing.xyz"))
    }

    @Test
    fun getKnownDomains() = runBlocking {
        dao.insert(DomainEntity("a.com", DomainCategory.WHITELIST, "test"))
        dao.insert(DomainEntity("b.com", DomainCategory.BLACKLIST, "test"))

        val results = dao.getKnownDomains()
        assertEquals(2, results.size)
        assertTrue(results.contains("a.com"))
        assertTrue(results.contains("b.com"))
    }

    @Test
    fun clearAllRemovesAll() = runBlocking {
        dao.insert(DomainEntity("test.com", DomainCategory.BLACKLIST, "test"))
        dao.clearAll()

        assertFalse(dao.isBlacklisted("test.com"))
        assertEquals(0, dao.getKnownDomains().size)
    }

    @Test
    fun insertReplacesExisting() = runBlocking {
        dao.insert(
            DomainEntity(
                domain = "example.com",
                category = DomainCategory.BLACKLIST,
                source = "test"
            )
        )
        assertTrue(dao.isBlacklisted("example.com"))

        dao.insert(
            DomainEntity(
                domain = "example.com",
                category = DomainCategory.WHITELIST,
                source = "test"
            )
        )
        assertTrue(dao.isWhitelisted("example.com"))
        assertFalse(dao.isBlacklisted("example.com"))
    }

    @Test
    fun unknownDomainReturnsFalse() = runBlocking {
        assertFalse(dao.isWhitelisted("nonexistent.com"))
        assertFalse(dao.isBlacklisted("nonexistent.com"))
    }
}
