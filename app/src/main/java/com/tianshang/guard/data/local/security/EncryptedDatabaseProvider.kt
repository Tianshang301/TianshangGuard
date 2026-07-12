package com.tianshang.guard.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import com.tianshang.guard.core.util.SecureLog
import com.tianshang.guard.data.local.database.AlertEntity
import com.tianshang.guard.data.local.database.AlertType
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.local.database.FeedbackEntity
import com.tianshang.guard.data.local.database.FeedbackLabel
import com.tianshang.guard.data.local.database.GuardDatabase
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.util.Base64

class EncryptedDatabaseProvider(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "guard_db_key"
        private const val PREFS_NAME = "guard_db_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val DB_NAME = "guard.db"

        // SQLCipher v4.5.4 default parameters — declared explicitly to ensure
        // compatibility if future SQLCipher versions change their defaults.
        // Must be kept in sync with the actual library version.
        private const val SQLCIPHER_VERSION = "4.5.4"
        private const val CIPHER_PAGE_SIZE = 4096
        private const val KDF_ITERATIONS = 256000
        private const val CIPHER_HMAC_ALGORITHM = "HMAC_SHA512"
        private const val CIPHER_KDF_ALGORITHM = "PBKDF2_HMAC_SHA512"

        internal val CIPHER_HOOK = object : SQLiteDatabaseHook {
            override fun preKey(database: SQLiteDatabase) {
                database.execSQL("PRAGMA cipher_page_size = $CIPHER_PAGE_SIZE")
                database.execSQL("PRAGMA kdf_iter = $KDF_ITERATIONS")
                database.execSQL("PRAGMA cipher_hmac_algorithm = $CIPHER_HMAC_ALGORITHM")
                database.execSQL("PRAGMA cipher_kdf_algorithm = $CIPHER_KDF_ALGORITHM")
            }

            override fun postKey(database: SQLiteDatabase) {
                // Verify settings took effect
                database.rawQuery("PRAGMA cipher_hmac_algorithm", null)?.use {
                    if (it.moveToFirst()) {
                        SecureLog.d("EncryptedDatabaseProvider", "cipher_hmac_algorithm = ${it.getString(0)}")
                    }
                }
            }
        }
    }

    fun createDatabase(): GuardDatabase {
        try {
            SQLiteDatabase.loadLibs(context)
        } catch (e: UnsatisfiedLinkError) {
            SecureLog.e("EncryptedDatabaseProvider", "SQLCipher native library not available", e)
            return buildFallbackDatabase()
        }
        val passphrase = getOrCreatePassphrase()
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        val factory = SupportFactory(passphraseBytes, CIPHER_HOOK)

        // Check if old unencrypted database exists and preserve data
        val dbFile = context.getDatabasePath(DB_NAME)
        var preservedData: MigrationData? = null
        if (dbFile.exists() && !isEncrypted(dbFile)) {
            SecureLog.i("EncryptedDatabaseProvider", "Migrating unencrypted database to encrypted")
            preservedData = readPlaintextDatabase(dbFile)
        }

        val db = Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            DB_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(
                GuardDatabase.MIGRATION_1_2,
                GuardDatabase.MIGRATION_2_3,
                GuardDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()

        // Write preserved data into the new encrypted database
        if (preservedData != null) {
            writeDataToEncryptedDb(db, preservedData)
        }

        return db
    }

    /**
     * Build a fallback database when SQLCipher native library is unavailable.
     * Uses plaintext Room database — no encryption, but avoids app crash.
     * This should only happen on unsupported devices (e.g. x86 emulators without ARM libs).
     */
    private fun buildFallbackDatabase(): GuardDatabase {
        SecureLog.w("EncryptedDatabaseProvider", "SQLCipher unavailable, building plaintext Room database")
        return Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            DB_NAME
        )
            .addMigrations(
                GuardDatabase.MIGRATION_1_2,
                GuardDatabase.MIGRATION_2_3,
                GuardDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Check if the database file is already encrypted by trying to open it as plaintext.
     */
    private fun isEncrypted(dbFile: File): Boolean {
        if (!dbFile.exists()) return false
        return try {
            val conn = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            conn.close()
            false
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Preserved data from an unencrypted database during plaintext-to-encrypted migration.
     */
    private data class MigrationData(
        val domains: List<DomainEntity>,
        val alerts: List<AlertEntity>,
        val feedback: List<FeedbackEntity>
    )

    /**
     * Read all data from an unencrypted database into memory, then delete the old database files.
     * Returns null if reading fails.
     */
    private fun readPlaintextDatabase(dbFile: File): MigrationData? {
        var oldDb: android.database.sqlite.SQLiteDatabase? = null
        try {
            oldDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            val domains = mutableListOf<DomainEntity>()
            try {
                oldDb.rawQuery("SELECT * FROM domains", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        domains.add(
                            DomainEntity(
                                domain = cursor.getString(cursor.getColumnIndexOrThrow("domain")),
                                category = DomainCategory.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("category"))),
                                source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                                addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("addedAt")),
                                confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence"))
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                SecureLog.w("EncryptedDatabaseProvider", "domains table not found in old database")
            }

            val alerts = mutableListOf<AlertEntity>()
            try {
                oldDb.rawQuery("SELECT * FROM alerts", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        alerts.add(
                            AlertEntity(
                                id = 0,
                                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                                type = AlertType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
                                domain = cursor.getString(cursor.getColumnIndexOrThrow("domain")),
                                url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                                riskLevel = cursor.getString(cursor.getColumnIndexOrThrow("riskLevel")),
                                userAction = cursor.getString(cursor.getColumnIndexOrThrow("userAction"))
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                SecureLog.w("EncryptedDatabaseProvider", "alerts table not found in old database")
            }

            val feedback = mutableListOf<FeedbackEntity>()
            try {
                oldDb.rawQuery("SELECT * FROM user_feedback", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        feedback.add(
                            FeedbackEntity(
                                id = 0,
                                timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                                textHash = cursor.getString(cursor.getColumnIndexOrThrow("textHash")),
                                tokens = cursor.getString(cursor.getColumnIndexOrThrow("tokens")),
                                modelScore = cursor.getFloat(cursor.getColumnIndexOrThrow("modelScore")),
                                label = FeedbackLabel.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("label"))),
                                source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                                features = cursor.getString(cursor.getColumnIndexOrThrow("features"))
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                SecureLog.w("EncryptedDatabaseProvider", "user_feedback table not found in old database")
            }

            SecureLog.i("EncryptedDatabaseProvider", "Old database read: ${domains.size} domains, ${alerts.size} alerts, ${feedback.size} feedback")
            return MigrationData(domains, alerts, feedback)
        } catch (e: Exception) {
            SecureLog.e("EncryptedDatabaseProvider", "Failed to read old database", e)
            return null
        } finally {
            try { oldDb?.close() } catch (_: Exception) {}
            // Delete old database files (including WAL and SHM)
            dbFile.delete()
            val parent = dbFile.parentFile
            if (parent != null) {
                File(parent, "${DB_NAME}-wal").delete()
                File(parent, "${DB_NAME}-shm").delete()
            }
        }
    }

    /**
     * Write preserved data into the newly created encrypted database.
     */
    private fun writeDataToEncryptedDb(db: GuardDatabase, data: MigrationData) {
        runBlocking {
            if (data.domains.isNotEmpty()) {
                data.domains.forEach { db.domainDao().insert(it) }
            }
            if (data.alerts.isNotEmpty()) {
                data.alerts.forEach { db.alertDao().insert(it) }
            }
            if (data.feedback.isNotEmpty()) {
                data.feedback.forEach { db.feedbackDao().insert(it) }
            }
        }
        SecureLog.i("EncryptedDatabaseProvider", "Migration data written to encrypted database")
    }

    private fun getOrCreatePassphrase(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return decryptPassphrase(existing)
        }

        // Generate new passphrase
        val newPassphrase = generatePassphrase()
        val encrypted = encryptPassphrase(newPassphrase)
        prefs.edit().putString(KEY_DB_PASSPHRASE, encrypted).apply()
        SecureLog.i("EncryptedDatabaseProvider", "New database passphrase generated")
        return newPassphrase
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encryptPassphrase(plain: String): String {
        val key = getOrCreateKey()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptPassphrase(encrypted: String): String {
        val key = getOrCreateKey()
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val data = combined.copyOfRange(12, combined.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
