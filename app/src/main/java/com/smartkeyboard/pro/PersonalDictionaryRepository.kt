package com.smartkeyboard.pro

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Cloud persistence for the personal dictionary.
 *
 * The Firestore instance is injected so only one named database instance is ever
 * created for the whole app process.
 */
class PersonalDictionaryRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
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

    /**
     * Reads the cloud dictionary.
     *
     * The documents are mapped by hand on purpose: `toObjects(DictionaryEntry::class.java)`
     * requires a no-argument constructor, which the immutable data class does not provide,
     * so it used to throw and silently return an empty list.
     */
    suspend fun fetchLearnedWords(): List<DictionaryEntry> {
        val uid = getUid() ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("learned_words").get().await()

            snapshot.documents.mapNotNull { doc ->
                val word = (doc.getString("word") ?: doc.id)?.trim().orEmpty()
                if (word.isEmpty()) {
                    null
                } else {
                    DictionaryEntry(
                        word = word.lowercase(),
                        frequency = doc.getLong("frequency")?.toInt() ?: 1,
                        lastUsed = doc.getLong("lastUsed") ?: System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
