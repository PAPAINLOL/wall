package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "view_history")
data class ViewHistoryEntity(
    @PrimaryKey val postId: String,
    val viewedAt: Long = System.currentTimeMillis()
)
