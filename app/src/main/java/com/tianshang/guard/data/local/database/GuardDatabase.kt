package com.tianshang.guard.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DomainEntity::class, AlertEntity::class, FeedbackEntity::class], exportSchema = false, version = 4)
abstract class GuardDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun alertDao(): AlertDao
    abstract fun feedbackDao(): FeedbackDao
}
