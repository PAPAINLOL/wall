package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subreddits")
data class SubredditEntity(
    @PrimaryKey val name: String,
    val displayName: String,
    val description: String,
    val iconEmoji: String,
    val isEnabled: Boolean,
    val isCustom: Boolean,
    val category: String,
    val orderIndex: Int = 0
)
