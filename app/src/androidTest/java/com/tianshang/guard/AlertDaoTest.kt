package com.tianshang.guard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tianshang.guard.data.local.database.AlertDao
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.local.database.GuardDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDaoTest {

    private lateinit var db: GuardDatabase
    private lateinit var dao: AlertDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alertDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryRecentAlerts() = runBlocking {
        dao.insert(alert(AlertType.SCREEN_SHARE))
        dao.insert(alert(AlertType.BLACKLIST_BLOCKED))
        val alerts = dao.getRecentAlerts(10).first()
        assertEquals(2, alerts.size)
    }

    @Test
    fun getBlockedCount() = runBlocking {
        dao.insert(alert(AlertType.BLACKLIST_BLOCKED))
        dao.insert(alert(AlertType.BLACKLIST_BLOCKED))
        dao.insert(alert(AlertType.VISITED))
        assertEquals(2, dao.getBlockedCount())
    }

    @Test
    fun getCountByType() = runBlocking {
        dao.insert(alert(AlertType.SMS_PHISHING))
        dao.insert(alert(AlertType.SMS_PHISHING))
        dao.insert(alert(AlertType.PHISHING_PAGE))
        assertEquals(2, dao.getCountByType(AlertType.SMS_PHISHING.name))
        assertEquals(1, dao.getCountByType(AlertType.PHISHING_PAGE.name))
    }

    @Test
    fun clearAll() = runBlocking {
        dao.insert(alert(AlertType.SUSPICIOUS_DOMAIN))
        dao.insert(alert(AlertType.VISITED))
        dao.clearAll()
        val alerts = dao.getRecentAlerts(10).first()
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun recentAlertsLimitedByParameter() = runBlocking {
        repeat(5) { dao.insert(alert(AlertType.VISITED)) }
        val alerts = dao.getRecentAlerts(3).first()
        assertEquals(3, alerts.size)
    }

    private fun alert(type: AlertType) = AlertEntity(
        type = type,
        domain = null,
        url = null,
        riskLevel = null,
        userAction = null
    )
}
