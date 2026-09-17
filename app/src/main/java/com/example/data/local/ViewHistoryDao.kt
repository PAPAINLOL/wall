package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ViewHistoryDao {
    @Query("SELECT postId FROM view_history")
    suspend fun getAllViewedIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM view_history WHERE postId = :postId)")
    suspend fun hasBeenViewed(postId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markViewed(entity: ViewHistoryEntity)

    @Query("DELETE FROM view_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM view_history")
    suspend fun getHistoryCount(): Int
}
