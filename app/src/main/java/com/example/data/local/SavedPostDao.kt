package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {
    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun getAllSavedPosts(): Flow<List<SavedPostEntity>>

    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC LIMIT 100")
    suspend fun getCachedPostsList(): List<SavedPostEntity>

    @Query("SELECT * FROM saved_posts WHERE isFavorite = 1 ORDER BY savedAt DESC")
    fun getFavoritePosts(): Flow<List<SavedPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: SavedPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<SavedPostEntity>)

    @Query("SELECT * FROM saved_posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: String): SavedPostEntity?

    @Query("UPDATE saved_posts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM saved_posts WHERE id = :id")
    suspend fun deletePost(id: String)

    @Query("DELETE FROM saved_posts WHERE isFavorite = 0")
    suspend fun clearNonFavorites()
}
