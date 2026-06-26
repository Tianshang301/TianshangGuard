package com.tianshang.guard.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity)

    @Query("DELETE FROM user_feedback WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFeedback(limit: Int): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFeedbackSync(limit: Int): List<FeedbackEntity>

    @Query("SELECT COUNT(*) FROM user_feedback WHERE label = :label")
    suspend fun getCountByLabel(label: FeedbackLabel): Int

    @Query("SELECT COUNT(*) FROM user_feedback")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestFeedback(): FeedbackEntity?

    @Query("DELETE FROM user_feedback")
    suspend fun clearAll()
}
