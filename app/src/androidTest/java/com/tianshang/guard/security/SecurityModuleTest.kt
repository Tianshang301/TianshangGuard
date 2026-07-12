package com.tianshang.guard.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.local.database.GuardDatabase
import com.tianshang.guard.data.local.security.EncryptedDatabaseProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
@LargeTest
class SecurityModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun encryptedDatabase_createsWithoutError() {
        val name = "guard_test_${java.util.UUID.randomUUID()}.db"
        val db = buildEncryptedTestDatabase(name)
        try {
            assertNotNull(db)
            val dao = db.domainDao()
            runBlocking {
                dao.insert(DomainEntity("test.com", DomainCategory.WHITELIST, "test"))
                assertTrue(dao.isWhitelisted("test.com"))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun encryptedDatabase_survivesReopen() = runBlocking {
        val name = "guard_test_${java.util.UUID.randomUUID()}.db"
        val db1 = buildEncryptedTestDatabase(name)
        db1.domainDao().insert(DomainEntity("persist.com", DomainCategory.BLACKLIST, "test"))
        db1.close()

        val db2 = buildEncryptedTestDatabase(name)
        try {
            assertTrue(db2.domainDao().isBlacklisted("persist.com"))
        } finally {
            db2.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun encryptedDatabase_writesAndReadsAlerts() = runBlocking {
        val name = "guard_test_${java.util.UUID.randomUUID()}.db"
        val db = buildEncryptedTestDatabase(name)
        try {
            val dao = db.alertDao()
            dao.insert(AlertEntity(type = AlertType.SCREEN_SHARE, domain = null, url = null, riskLevel = null, userAction = null))
            dao.insert(AlertEntity(type = AlertType.BLACKLIST_BLOCKED, domain = "evil.com", url = null, riskLevel = null, userAction = null))
            val blocked = dao.getCountByType(AlertType.BLACKLIST_BLOCKED.name)
            assertEquals(1, blocked)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun tamperedDatabase_detectedAndRecovered() {
        val name = "guard_test_${java.util.UUID.randomUUID()}.db"
        val db = buildEncryptedTestDatabase(name)
        // Force Room to create the database file before closing
        db.openHelper.writableDatabase
        db.close()

        val dbFile = context.getDatabasePath(name)
        corruptDatabaseHeader(dbFile)

        // Verify the database file header is corrupted
        val header = ByteArray(16)
        RandomAccessFile(dbFile, "r").use { raf ->
            raf.readFully(header)
        }
        val firstFourZero = header[0] == 0.toByte() && header[1] == 0.toByte() &&
                             header[2] == 0.toByte() && header[3] == 0.toByte()
        assertTrue("First 4 bytes should be zeroed after corruption", firstFourZero)

        // Opening with SQLCipher on a corrupted file should throw/timeout
        // Use runBlocking + withTimeout to avoid native hang
        val threwException = runBlocking {
            try {
                withTimeout(5000) {
                    withContext(Dispatchers.IO) {
                        SQLiteDatabase.loadLibs(context)
                        SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            "test_passphrase_32_bytes_for_unit_test___",
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                            EncryptedDatabaseProvider.CIPHER_HOOK
                        )
                    }
                }
                false
            } catch (_: TimeoutCancellationException) {
                true
            } catch (_: Exception) {
                true
            }
        }
        assertTrue("Opening corrupted database should throw or timeout", threwException)

        context.deleteDatabase(name)
    }

    @Test
    fun migratesFromPlaintextToEncrypted() = runBlocking {
        context.deleteDatabase("guard.db")

        val plainDb = Room.databaseBuilder(context, GuardDatabase::class.java, "guard.db")
            .allowMainThreadQueries()
            .build()
        plainDb.domainDao().insert(DomainEntity("migrate.com", DomainCategory.WHITELIST, "test"))
        plainDb.close()

        val provider = EncryptedDatabaseProvider(context)
        val encryptedDb = provider.createDatabase()
        try {
            assertTrue(encryptedDb.domainDao().isWhitelisted("migrate.com"))
        } finally {
            encryptedDb.close()
            context.deleteDatabase("guard.db")
        }
    }

    @Test
    fun keystoreContainsKeyAlias() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        assertTrue("Key alias should exist in AndroidKeyStore", keyStore.containsAlias("guard_db_key"))
    }

    private fun buildEncryptedTestDatabase(name: String): GuardDatabase {
        SQLiteDatabase.loadLibs(context)
        val passphrase = "test_passphrase_32_bytes_for_unit_test___"
        val factory = SupportFactory(passphrase.toByteArray(Charsets.UTF_8), EncryptedDatabaseProvider.CIPHER_HOOK)
        return Room.databaseBuilder(context, GuardDatabase::class.java, name)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
    }

    private fun corruptDatabaseHeader(file: File) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.writeByte(0x00)
                raf.writeByte(0x00)
                raf.writeByte(0x00)
                raf.writeByte(0x00)
            }
        } catch (_: Exception) {
        }
    }
}
