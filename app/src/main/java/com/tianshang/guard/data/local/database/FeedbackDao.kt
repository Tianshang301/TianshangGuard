package com.tianshang.guard.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity)

    @Update
    suspend fun update(feedback: FeedbackEntity)

    @Query("DELETE FROM user_feedback WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM user_feedback WHERE textHash = :textHash LIMIT 1")
    suspend fun getByTextHash(textHash: String): FeedbackEntity?

    @Query("SELECT * FROM user_feedback WHERE label = :label ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByLabel(label: FeedbackLabel, limit: Int): List<FeedbackEntity>

    @Query("SELECT * FROM user_feedback ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestFeedback(limit: Int): List<FeedbackEntity>

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
