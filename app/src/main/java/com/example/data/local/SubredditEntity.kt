package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SubredditInfo

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
) {
    fun toModel(): SubredditInfo = SubredditInfo(
        name = name,
        displayName = displayName,
        description = description,
        iconEmoji = iconEmoji,
        isEnabled = isEnabled,
        isCustom = isCustom,
        category = category
    )
}
