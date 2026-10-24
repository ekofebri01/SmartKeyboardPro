package com.smartkeyboard.pro

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class DictionaryEntry(
    val word: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

/**
 * Personal Dictionary manager with persistent deletion blacklist and cache invalidation.
 * Prevents deleted words from reappearing in suggestion previews or being auto-relearned.
 */
object PersonalDictionary {
    private const val PREFS_NAME = "smart_keyboard_personal_dict"
    private const val KEY_WORDS_JSON = "learned_words_json"
    private const val KEY_DELETED_WORDS_JSON = "deleted_words_json"
    private const val KEY_AUTO_LEARN = "auto_learn_enabled"

    private val wordMap = mutableMapOf<String, DictionaryEntry>()
    private val deletedWordsSet = mutableSetOf<String>()
    private var sortedWords: List<DictionaryEntry> = emptyList()
    private var isLoaded = false

    private val defaultStarterWords = listOf(
        // Indonesian common words
        "yang", "dan", "di", "ini", "itu", "dengan", "untuk", "tidak", "ada", "dari",
        "saya", "kamu", "bisa", "akan", "sudah", "ke", "pada", "juga", "atau", "mereka",
        "kita", "terima", "kasih", "halo", "selamat", "pagi", "siang", "malam", "apa",
        "bagaimana", "kapan", "dimana", "kenapa", "tolong", "mohon", "maaf", "baik",
        "siap", "sama", "lagi", "mau", "tahu", "hanya", "saja", "tentang", "banyak",
        "rumah", "kantor", "sekarang", "besok", "nanti", "cepat", "bagus", "oke",
        // English common words
        "the", "and", "is", "you", "that", "it", "to", "in", "was", "for", "on", "are",
        "as", "with", "his", "they", "at", "be", "this", "have", "from", "one", "had",
        "word", "but", "not", "what", "all", "were", "we", "when", "your", "can", "said",
        "there", "use", "an", "each", "which", "she", "do", "how", "their", "if", "will",
        "up", "other", "about", "out", "many", "then", "them", "these", "so", "some"
    )

    @Synchronized
    fun load(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Load deleted words blacklist
        deletedWordsSet.clear()
        val deletedJson = prefs.getString(KEY_DELETED_WORDS_JSON, null)
        if (!deletedJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(deletedJson)
                for (i in 0 until array.length()) {
                    deletedWordsSet.add(array.getString(i).lowercase(Locale.ROOT))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Preload default starter words (skipping deleted ones)
        defaultStarterWords.forEach { w ->
            val lower = w.lowercase(Locale.ROOT)
            if (!deletedWordsSet.contains(lower)) {
                wordMap[lower] = DictionaryEntry(word = lower, frequency = 5)
            }
        }

        // 3. Load learned words JSON (skipping deleted ones)
        val jsonStr = prefs.getString(KEY_WORDS_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val w = obj.getString("word").lowercase(Locale.ROOT)
                    if (!deletedWordsSet.contains(w)) {
                        val freq = obj.optInt("frequency", 1)
                        val time = obj.optLong("lastUsed", System.currentTimeMillis())
                        wordMap[w] = DictionaryEntry(word = w, frequency = freq, lastUsed = time)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        sortedWords = emptyList()
        isLoaded = true
    }

    @Synchronized
    fun learnWord(context: Context, rawWord: String) {
        val cleanRaw = rawWord.trim().lowercase(Locale.ROOT)
        val clean = cleanRaw.filter { it.isLetter() }
        if (clean.length < 2) return

        load(context)

        // CRITICAL FIX: If word was deleted by user, auto-learning MUST NOT re-add it!
        if (deletedWordsSet.contains(clean)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoLearn = prefs.getBoolean(KEY_AUTO_LEARN, true)
        if (!autoLearn) return

        val existing = wordMap[clean]
        val newFreq = (existing?.frequency ?: 0) + 1
        wordMap[clean] = DictionaryEntry(
            word = clean,
            frequency = newFreq,
            lastUsed = System.currentTimeMillis()
        )

        sortedWords = emptyList() // Reset cache
        save(context)
    }

    @Synchronized
    fun addWordManually(context: Context, rawWord: String) {
        val cleanRaw = rawWord.trim().lowercase(Locale.ROOT)
        val clean = cleanRaw.filter { it.isLetter() }
        if (clean.length < 2) return

        load(context)

        // Un-blacklist word if user manually adds it
        deletedWordsSet.remove(clean)

        val existing = wordMap[clean]
        val newFreq = (existing?.frequency ?: 0) + 2
        wordMap[clean] = DictionaryEntry(
            word = clean,
            frequency = newFreq,
            lastUsed = System.currentTimeMillis()
        )

        sortedWords = emptyList() // Reset cache
        save(context)
    }

    @Synchronized
    private fun ensureSorted() {
        if (sortedWords.isEmpty() || sortedWords.size != wordMap.size) {
            sortedWords = wordMap.values.sortedWith(
                compareByDescending<DictionaryEntry> { it.frequency }
                    .thenByDescending { it.lastUsed }
            )
        }
    }

    @Synchronized
    fun getSuggestions(context: Context, prefix: String, limit: Int = 4): List<String> {
        load(context)
        ensureSorted()
        val clean = prefix.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return emptyList()

        val matches = sortedWords
            .filter { !deletedWordsSet.contains(it.word) && it.word.startsWith(clean) }
            .map { it.word }
            .toMutableList()

        // If prefix itself is not blacklisted and not yet in suggestions, offer it
        if (!deletedWordsSet.contains(clean) && !matches.contains(clean)) {
            matches.add(0, clean)
        }

        return matches.distinct().filter { !deletedWordsSet.contains(it) }.take(limit)
    }

    @Synchronized
    fun getAllLearnedWords(context: Context): List<DictionaryEntry> {
        load(context)
        return wordMap.values
            .filter { !deletedWordsSet.contains(it.word) }
            .sortedWith(compareByDescending<DictionaryEntry> { it.frequency }.thenBy { it.word })
            .toList()
    }

    @Synchronized
    fun removeWord(context: Context, word: String) {
        load(context)
        val clean = word.trim().lowercase(Locale.ROOT)

        // 1. Remove from active map
        wordMap.remove(clean)

        // 2. Add to persistent deleted blacklist
        deletedWordsSet.add(clean)

        // 3. Clear cache
        sortedWords = emptyList()

        // 4. Save changes
        save(context)
    }

    @Synchronized
    fun clearUserDictionary(context: Context) {
        load(context)
        // Blacklist all current words
        deletedWordsSet.addAll(wordMap.keys)
        wordMap.clear()
        sortedWords = emptyList()
        save(context)
    }

    @Synchronized
    private fun save(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Save active words
            val wordsArray = JSONArray()
            wordMap.values.forEach { entry ->
                val obj = JSONObject().apply {
                    put("word", entry.word)
                    put("frequency", entry.frequency)
                    put("lastUsed", entry.lastUsed)
                }
                wordsArray.put(obj)
            }

            // Save deleted words blacklist
            val deletedArray = JSONArray()
            deletedWordsSet.forEach { deletedArray.put(it) }

            prefs.edit()
                .putString(KEY_WORDS_JSON, wordsArray.toString())
                .putString(KEY_DELETED_WORDS_JSON, deletedArray.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
