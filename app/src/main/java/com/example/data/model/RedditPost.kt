package com.example.data.model

data class RedditPost(
    val id: String,
    val subreddit: String,
    val title: String,
    val author: String,
    val imageUrl: String,
    val permalink: String = "",
    val score: Int = 0,
    val numComments: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val isLocal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val localFilePath: String? = null
)
