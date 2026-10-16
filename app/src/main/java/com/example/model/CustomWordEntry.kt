package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "custom_words")
@JsonClass(generateAdapter = true)
data class CustomWordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val frequency: Int = 1
)
