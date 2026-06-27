package com.tianshang.guard.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import com.tianshang.guard.data.local.database.GuardDatabase
import com.tianshang.guard.core.util.SecureLog
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
    }

    fun createDatabase(): GuardDatabase {
        val passphrase = getOrCreatePassphrase()
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        val factory = SupportFactory(passphraseBytes)

        // Check if old unencrypted database exists
        val dbFile = context.getDatabasePath(DB_NAME)
        if (dbFile.exists() && !isEncrypted(dbFile)) {
            SecureLog.i("EncryptedDatabaseProvider", "Migrating unencrypted database to encrypted")
            migrateToEncrypted(dbFile, passphraseBytes)
        }

        return Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            DB_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries() // TODO: Remove after migrating all DAO calls to suspend
            .build()
    }

    /**
     * Check if the database file is already encrypted by trying to read it as plaintext.
     */
    private fun isEncrypted(dbFile: File): Boolean {
        return try {
            // Try to open as plaintext SQLite - if it fails, it's encrypted
            val conn = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            conn.close()
            false // Successfully opened as plaintext = not encrypted
        } catch (e: Exception) {
            true // Failed to open as plaintext = encrypted
        }
    }

    /**
     * Migrate unencrypted database to encrypted database.
     */
    private fun migrateToEncrypted(dbFile: File, passphrase: ByteArray) {
        try {
            // 1. Open unencrypted database
            val unencrypted = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            // 2. Create temporary encrypted database
            val tempFile = File(dbFile.parent, "${DB_NAME}.encrypted")
            val encrypted = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                passphrase.toString(Charsets.UTF_8),
                null,
                net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE or
                    net.sqlcipher.database.SQLiteDatabase.CREATE_IF_NECESSARY
            )

            // 3. Copy data
            unencrypted.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '${passphrase.toString(Charsets.UTF_8)}'")
            unencrypted.execSQL("SELECT sqlcipher_export('encrypted')")
            unencrypted.execSQL("DETACH DATABASE encrypted")

            // 4. Close databases
            encrypted.close()
            unencrypted.close()

            // 5. Replace old database with encrypted one
            dbFile.delete()
            tempFile.renameTo(dbFile)

            SecureLog.i("EncryptedDatabaseProvider", "Database migration completed successfully")
        } catch (e: Exception) {
            SecureLog.e("EncryptedDatabaseProvider", "Migration failed, will recreate database", e)
            // If migration fails, delete old database and let Room recreate it
            dbFile.delete()
        }
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
