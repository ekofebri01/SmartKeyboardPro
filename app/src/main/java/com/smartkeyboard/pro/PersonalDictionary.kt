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

object PersonalDictionary {
    private const val PREFS_NAME = "smart_keyboard_personal_dict"
    private const val KEY_WORDS_JSON = "learned_words_json"
    private const val KEY_AUTO_LEARN = "auto_learn_enabled"

    private val wordMap = mutableMapOf<String, DictionaryEntry>()
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

        // Preload default words
        defaultStarterWords.forEach { w ->
            val lower = w.lowercase(Locale.ROOT)
            wordMap[lower] = DictionaryEntry(word = lower, frequency = 5)
        }

        val jsonStr = prefs.getString(KEY_WORDS_JSON, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val w = obj.getString("word").lowercase(Locale.ROOT)
                    val freq = obj.optInt("frequency", 1)
                    val time = obj.optLong("lastUsed", System.currentTimeMillis())
                    wordMap[w] = DictionaryEntry(word = w, frequency = freq, lastUsed = time)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoaded = true
    }

    @Synchronized
    fun learnWord(context: Context, rawWord: String) {
        val cleanRaw = rawWord.trim().lowercase(Locale.ROOT)
        val clean = cleanRaw.filter { it.isLetter() }
        if (clean.length < 2) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoLearn = prefs.getBoolean(KEY_AUTO_LEARN, true)
        if (!autoLearn) return

        load(context)
        val existing = wordMap[clean]
        val newFreq = (existing?.frequency ?: 0) + 1
        wordMap[clean] = DictionaryEntry(
            word = clean,
            frequency = newFreq,
            lastUsed = System.currentTimeMillis()
        )

        save(context)
        sortedWords = emptyList() // Reset cache
    }

    private var sortedWords: List<DictionaryEntry> = emptyList()

    @Synchronized
    private fun ensureSorted() {
        if (sortedWords.size != wordMap.size) {
            sortedWords = wordMap.values.sortedWith(
                compareByDescending<DictionaryEntry> { it.frequency }
                    .thenByDescending { it.lastUsed }
            )
        }
    }

    @Synchronized
    fun getSuggestions(context: Context, prefix: String, limit: Int = 3): List<String> {
        load(context)
        ensureSorted()
        val clean = prefix.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return emptyList()

        val matches = sortedWords
            .filter { it.word.startsWith(clean) }
            .map { it.word }
            .toMutableList()

        // If prefix is not yet in suggestions, offer it as an option
        if (!matches.contains(clean)) {
            matches.add(0, clean)
        }

        return matches.distinct().take(limit)
    }

    @Synchronized
    fun getAllLearnedWords(context: Context): List<DictionaryEntry> {
        load(context)
        return wordMap.values
            .sortedWith(compareByDescending<DictionaryEntry> { it.frequency }.thenBy { it.word })
            .toList()
    }

    @Synchronized
    fun removeWord(context: Context, word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        wordMap.remove(clean)
        sortedWords = emptyList() // Reset cache
        save(context)
    }

    @Synchronized
    fun clearUserDictionary(context: Context) {
        wordMap.clear()
        defaultStarterWords.forEach { w ->
            val lower = w.lowercase(Locale.ROOT)
            wordMap[lower] = DictionaryEntry(word = lower, frequency = 5)
        }
        sortedWords = emptyList() // Reset cache
        save(context)
    }

    @Synchronized
    private fun save(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            wordMap.values.forEach { entry ->
                val obj = JSONObject().apply {
                    put("word", entry.word)
                    put("frequency", entry.frequency)
                    put("lastUsed", entry.lastUsed)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_WORDS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
