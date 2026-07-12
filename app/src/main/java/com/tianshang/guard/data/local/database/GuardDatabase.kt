package com.tianshang.guard.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DomainEntity::class, AlertEntity::class, FeedbackEntity::class], exportSchema = false, version = 4)
abstract class GuardDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun alertDao(): AlertDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change — only DAO method signatures changed to suspend
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_feedback` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `textHash` TEXT NOT NULL,
                        `tokens` TEXT NOT NULL,
                        `modelScore` REAL NOT NULL,
                        `label` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `features` TEXT
                    )
                """)
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema change — only DAO method signatures changed to suspend
            }
        }
    }
}
