package com.smartkeyboard.pro

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class AutoTextRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val dbId: String = context.getString(R.string.firestore_database_id)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(dbId)

    private fun getUid(): String? = auth.currentUser?.uid

    suspend fun saveAutoTextToCloud(entry: AutoTextEntry): Boolean {
        val uid = getUid() ?: return false
        return try {
            val docRef = firestore.collection("users").document(uid)
                .collection("auto_text_shortcuts").document(entry.id)
            val data = mapOf(
                "id" to entry.id,
                "shortcut" to entry.shortcut.trim().lowercase(),
                "fullText" to entry.fullText.trim(),
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun fetchAutoTextsFromCloud(): List<AutoTextEntry> {
        val uid = getUid() ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("auto_text_shortcuts").get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                AutoTextEntry.fromFirestoreDoc(data, doc.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun removeAutoTextFromCloud(id: String): Boolean {
        val uid = getUid() ?: return false
        return try {
            firestore.collection("users").document(uid)
                .collection("auto_text_shortcuts").document(id)
                .delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Pushes every shortcut in one batch instead of one network round-trip per entry.
     */
    suspend fun syncAllAutoTextsToCloud(entries: List<AutoTextEntry>): Boolean {
        val uid = getUid() ?: return false
        return try {
            val collection = firestore.collection("users").document(uid)
                .collection("auto_text_shortcuts")

            if (entries.isEmpty()) return true

            // A Firestore batch is limited to 500 writes, so commit in chunks.
            entries.chunked(MAX_BATCH_WRITES).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { entry ->
                    batch.set(
                        collection.document(entry.id),
                        mapOf(
                            "id" to entry.id,
                            "shortcut" to entry.shortcut.trim().lowercase(),
                            "fullText" to entry.fullText.trim(),
                            "userId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private companion object {
        const val MAX_BATCH_WRITES = 400
    }
}
