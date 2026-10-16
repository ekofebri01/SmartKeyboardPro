package com.example.data.prediction

import com.example.model.AutoTextEntry
import java.util.Locale

class PredictionEngine {

    // Common Indonesian words for stable, snappy typing
    private val indonesianDictionary = listOf(
        "yang", "dan", "di", "ini", "itu", "dengan", "untuk", "tidak", "ada", "saya",
        "kamu", "bisa", "akan", "dari", "sudah", "mereka", "kita", "mau", "lagi", "sekarang",
        "terima", "kasih", "apa", "siapa", "mana", "bagaimana", "kenapa", "baik", "jadi", "hanya",
        "juga", "lebih", "tapi", "tetapi", "karena", "kalau", "bukan", "banyak", "orang", "hari",
        "waktu", "bisa", "harus", "bantu", "kerja", "sama", "sampai", "kemarin", "besok", "nanti",
        "banget", "mungkin", "paling", "selalu", "sudah", "belum", "pernah", "tahu", "paham", "makan",
        "minum", "tidur", "jalan", "pergi", "datang", "pulang", "rumah", "kantor", "sekolah", "teman",
        "saudara", "keluarga", "kabar", "halo", "selamat", "pagi", "siang", "sore", "malam", "sehat",
        "semoga", "sukses", "lancar", "semangat", "siap", "oke", "mantap", "makasih", "gimana", "dimana"
    )

    // Common English words
    private val englishDictionary = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "great", "hello", "thanks", "thank", "please", "welcome", "sorry", "love", "night", "morning"
    )

    private val baseIndonesianWords: Set<String> = indonesianDictionary.toSet()
    private val baseEnglishWords: Set<String> = englishDictionary.toSet()

    private val downloadedIndonesianWords = mutableSetOf<String>()
    private val downloadedEnglishWords = mutableSetOf<String>()

    private val customWords = mutableMapOf<String, Int>()

    @Volatile
    private var activeLanguageCode: String = "ALL" // "ALL", "ID", "EN"

    fun setDownloadedIndonesianWords(words: Set<String>) {
        synchronized(downloadedIndonesianWords) {
            downloadedIndonesianWords.clear()
            downloadedIndonesianWords.addAll(words)
        }
    }

    fun setDownloadedEnglishWords(words: Set<String>) {
        synchronized(downloadedEnglishWords) {
            downloadedEnglishWords.clear()
            downloadedEnglishWords.addAll(words)
        }
    }

    fun setActiveLanguage(langCode: String) {
        activeLanguageCode = langCode.uppercase(Locale.ROOT)
    }

    fun updateCustomWords(words: Map<String, Int>) {
        synchronized(customWords) {
            customWords.clear()
            customWords.putAll(words)
        }
    }

    fun recordLearnedWord(word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length < 2) return
        synchronized(customWords) {
            val count = customWords[clean] ?: 0
            customWords[clean] = count + 1
        }
    }

    fun getLearnedWordsSnapshot(): Map<String, Int> {
        return synchronized(customWords) {
            customWords.toMap()
        }
    }

    /**
     * Given the current word prefix and available auto-texts,
     * returns a list of candidate suggestions.
     * The first item may be an exact auto-text replacement if matched.
     */
    fun getSuggestions(
        prefix: String,
        activeAutoTexts: List<AutoTextEntry>,
        maxSuggestions: Int = 4
    ): SuggestionResult {
        val clean = prefix.trim()
        if (clean.isEmpty()) {
            return SuggestionResult(emptyList(), null)
        }

        val lowerPrefix = clean.lowercase(Locale.ROOT)

        // 1. Check exact or prefix Auto-Text match
        val exactAutoText = activeAutoTexts.firstOrNull {
            it.shortcut.equals(clean, ignoreCase = true)
        }
        val prefixAutoText = if (exactAutoText == null) {
            activeAutoTexts.firstOrNull {
                it.shortcut.lowercase(Locale.ROOT).startsWith(lowerPrefix)
            }
        } else null

        val topAutoText = exactAutoText ?: prefixAutoText

        val list = mutableListOf<String>()

        // If exact auto-text, offer the replacement prominently
        if (exactAutoText != null) {
            list.add(exactAutoText.replacement)
        }

        // Custom words match with high frequency
        val customMatches = synchronized(customWords) {
            customWords.filterKeys { it.startsWith(lowerPrefix) }
                .entries.sortedByDescending { it.value }
                .map { it.key }
        }

        for (w in customMatches) {
            if (list.size >= maxSuggestions) break
            val formatted = formatWordMatch(clean, w)
            if (!list.contains(formatted)) {
                list.add(formatted)
            }
        }

        // Dictionary matches (base + downloaded) based on active language setting
        val dictCandidates = mutableSetOf<String>()
        val lang = activeLanguageCode

        if (lang == "ALL" || lang == "ID") {
            dictCandidates.addAll(baseIndonesianWords)
            synchronized(downloadedIndonesianWords) {
                dictCandidates.addAll(downloadedIndonesianWords)
            }
        }

        if (lang == "ALL" || lang == "EN") {
            dictCandidates.addAll(baseEnglishWords)
            synchronized(downloadedEnglishWords) {
                dictCandidates.addAll(downloadedEnglishWords)
            }
        }

        val staticMatches = dictCandidates.filter { it.startsWith(lowerPrefix) }
            .sortedBy { it.length }

        for (w in staticMatches) {
            if (list.size >= maxSuggestions) break
            val formatted = formatWordMatch(clean, w)
            if (!list.contains(formatted)) {
                list.add(formatted)
            }
        }

        // If prefix auto-text was found and not yet added, add it
        if (prefixAutoText != null && !list.contains(prefixAutoText.replacement)) {
            list.add(0, prefixAutoText.replacement)
        }

        // Ensure user typed text is in the suggestions list if nothing matched or as first fallback
        if (list.isEmpty()) {
            list.add(clean)
        }

        return SuggestionResult(list.take(maxSuggestions), topAutoText)
    }

    private fun formatWordMatch(originalInput: String, match: String): String {
        return when {
            originalInput.all { it.isUpperCase() } -> match.uppercase(Locale.ROOT)
            originalInput.firstOrNull()?.isUpperCase() == true -> match.replaceFirstChar { it.uppercase() }
            else -> match
        }
    }
}

data class SuggestionResult(
    val suggestions: List<String>,
    val matchedAutoText: AutoTextEntry?
)
