package com.smartkeyboard.pro

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.content.Context

class PersonalDictionaryRepository(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val dbId = context.getString(R.string.firestore_database_id)
    private val firestore = FirebaseFirestore.getInstance(dbId)

    private fun getUid(): String? = auth.currentUser?.uid

    suspend fun syncLearnedWord(entry: DictionaryEntry) {
        val uid = getUid() ?: return
        try {
            firestore.collection("users").document(uid)
                .collection("learned_words").document(entry.word)
                .set(entry, SetOptions.merge()).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchLearnedWords(): List<DictionaryEntry> {
        val uid = getUid() ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("learned_words").get().await()
            snapshot.toObjects(DictionaryEntry::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun removeLearnedWord(word: String) {
        val uid = getUid() ?: return
        try {
            firestore.collection("users").document(uid)
                .collection("learned_words").document(word).delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
