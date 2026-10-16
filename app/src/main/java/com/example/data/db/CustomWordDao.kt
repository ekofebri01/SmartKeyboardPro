package com.example.data.db

import androidx.room.*
import com.example.model.CustomWordEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomWordDao {
    @Query("SELECT * FROM custom_words ORDER BY frequency DESC, word ASC")
    fun getAllFlow(): Flow<List<CustomWordEntry>>

    @Query("SELECT * FROM custom_words ORDER BY frequency DESC, word ASC")
    suspend fun getAll(): List<CustomWordEntry>

    @Query("SELECT * FROM custom_words WHERE LOWER(word) LIKE LOWER(:prefix) || '%' ORDER BY frequency DESC LIMIT 10")
    suspend fun findWordsByPrefix(prefix: String): List<CustomWordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CustomWordEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CustomWordEntry>)

    @Query("UPDATE custom_words SET frequency = frequency + 1 WHERE LOWER(word) = LOWER(:word)")
    suspend fun incrementFrequency(word: String): Int

    @Delete
    suspend fun delete(entry: CustomWordEntry)

    @Query("DELETE FROM custom_words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_words")
    suspend fun clearAll()
}
