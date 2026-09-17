package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubredditDao {
    @Query("SELECT * FROM subreddits ORDER BY isCustom DESC, orderIndex ASC, name ASC")
    fun getAllSubreddits(): Flow<List<SubredditEntity>>

    @Query("SELECT * FROM subreddits WHERE isEnabled = 1")
    suspend fun getEnabledSubreddits(): List<SubredditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubreddits(subreddits: List<SubredditEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubreddit(subreddit: SubredditEntity)

    @Update
    suspend fun updateSubreddit(subreddit: SubredditEntity)

    @Query("UPDATE subreddits SET isEnabled = :isEnabled WHERE name = :name")
    suspend fun setSubredditEnabled(name: String, isEnabled: Boolean)

    @Query("DELETE FROM subreddits WHERE name = :name")
    suspend fun deleteSubreddit(name: String)

    @Query("SELECT COUNT(*) FROM subreddits")
    suspend fun getSubredditCount(): Int
}
