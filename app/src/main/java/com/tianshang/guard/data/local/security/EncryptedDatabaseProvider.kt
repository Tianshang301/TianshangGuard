package com.tianshang.guard.data.local.security

import android.content.Context
import androidx.room.Room
import com.tianshang.guard.data.local.database.GuardDatabase

class EncryptedDatabaseProvider(private val context: Context) {

    fun createDatabase(): GuardDatabase {
        return Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            "guard.db"
        )
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }
}
