package com.example.data.db

import androidx.room.*
import com.example.model.AutoTextEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoTextDao {
    @Query("SELECT * FROM auto_texts ORDER BY shortcut ASC")
    fun getAllFlow(): Flow<List<AutoTextEntry>>

    @Query("SELECT * FROM auto_texts ORDER BY shortcut ASC")
    suspend fun getAll(): List<AutoTextEntry>

    @Query("SELECT * FROM auto_texts WHERE enabled = 1")
    suspend fun getEnabledList(): List<AutoTextEntry>

    @Query("SELECT * FROM auto_texts WHERE LOWER(shortcut) = LOWER(:shortcut) AND enabled = 1 LIMIT 1")
    suspend fun findByShortcut(shortcut: String): AutoTextEntry?

    @Query("SELECT * FROM auto_texts WHERE LOWER(shortcut) LIKE LOWER(:prefix) || '%' AND enabled = 1 LIMIT 5")
    suspend fun findMatchingPrefix(prefix: String): List<AutoTextEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AutoTextEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AutoTextEntry>)

    @Update
    suspend fun update(entry: AutoTextEntry)

    @Delete
    suspend fun delete(entry: AutoTextEntry)

    @Query("DELETE FROM auto_texts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM auto_texts")
    suspend fun clearAll()
}
