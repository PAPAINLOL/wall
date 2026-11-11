package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.RedditPost

@Entity(tableName = "saved_posts")
data class SavedPostEntity(
    @PrimaryKey val id: String,
    val subreddit: String,
    val title: String,
    val author: String,
    val imageUrl: String,
    val permalink: String,
    val score: Int,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false,
    val isLocal: Boolean = false,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toModel(): RedditPost = RedditPost(
        id = id,
        subreddit = subreddit,
        title = title,
        author = author,
        imageUrl = imageUrl,
        permalink = permalink,
        score = score,
        width = width,
        height = height,
        isLocal = isLocal
    )
}
