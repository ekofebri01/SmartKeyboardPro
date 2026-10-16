package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "auto_texts")
@JsonClass(generateAdapter = true)
data class AutoTextEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shortcut: String,
    val replacement: String,
    val category: String = "General",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
