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

    suspend fun syncAllAutoTextsToCloud(entries: List<AutoTextEntry>): Boolean {
        val uid = getUid() ?: return false
        return try {
            val collection = firestore.collection("users").document(uid)
                .collection("auto_text_shortcuts")
            entries.forEach { entry ->
                val data = mapOf(
                    "id" to entry.id,
                    "shortcut" to entry.shortcut.trim().lowercase(),
                    "fullText" to entry.fullText.trim(),
                    "userId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                collection.document(entry.id).set(data, SetOptions.merge()).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
