package com.example.data.dictionary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class DictionaryLanguage(val code: String, val displayName: String, val wordCountApprox: String) {
    INDONESIAN("ID", "Bahasa Indonesia", "~5.000+ kata"),
    ENGLISH("EN", "English (US/UK)", "~5.000+ words")
}

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progressPercent: Int, val message: String) : DownloadStatus()
    data class Success(val language: DictionaryLanguage, val totalWords: Int) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

class DictionaryManager(private val context: Context) {

    private val dictDir = File(context.filesDir, "dictionaries").apply { mkdirs() }

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    fun getDictionaryFile(language: DictionaryLanguage): File {
        return File(dictDir, "dict_${language.code.lowercase(Locale.ROOT)}.txt")
    }

    fun isDictionaryInstalled(language: DictionaryLanguage): Boolean {
        val file = getDictionaryFile(language)
        return file.exists() && file.length() > 100
    }

    fun getInstalledWordCount(language: DictionaryLanguage): Int {
        val file = getDictionaryFile(language)
        if (!file.exists()) return 0
        return try {
            file.bufferedReader().useLines { lines -> lines.count { it.isNotBlank() } }
        } catch (e: Exception) {
            0
        }
    }

    fun deleteDictionary(language: DictionaryLanguage): Boolean {
        val file = getDictionaryFile(language)
        return if (file.exists()) file.delete() else false
    }

    suspend fun loadDictionaryWords(language: DictionaryLanguage): Set<String> = withContext(Dispatchers.IO) {
        val file = getDictionaryFile(language)
        if (!file.exists()) return@withContext emptySet()
        val set = mutableSetOf<String>()
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val clean = line.trim().lowercase(Locale.ROOT)
                    if (clean.length >= 2) {
                        set.add(clean)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        set
    }

    /**
     * Downloads or generates the dictionary package.
     * Uses online open repository with built-in comprehensive offline fallback corpus.
     */
    suspend fun downloadDictionary(
        language: DictionaryLanguage,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        _downloadStatus.value = DownloadStatus.Downloading(10, "Menghubungkan server kamus...")
        onProgress(10, "Menghubungkan...")

        val words = mutableSetOf<String>()

        // 1. Try downloading from official open word lists
        val urls = when (language) {
            DictionaryLanguage.INDONESIAN -> listOf(
                "https://raw.githubusercontent.com/damzaky/kumpulan-kata-bahasa-indonesia/master/indonesian-words.txt",
                "https://raw.githubusercontent.com/masdevid/ID-Wordlist/master/indonesian-words.txt"
            )
            DictionaryLanguage.ENGLISH -> listOf(
                "https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-no-swears.txt",
                "https://raw.githubusercontent.com/dwyl/english-words/master/words_alpha.txt"
            )
        }

        var downloadedFromNet = false

        for (urlStr in urls) {
            try {
                _downloadStatus.value = DownloadStatus.Downloading(30, "Mengunduh berkas kata ${language.displayName}...")
                onProgress(30, "Mengunduh...")

                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SmartKeyboardPro-Android")
                }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var count = 0
                    reader.useLines { lines ->
                        for (raw in lines) {
                            val w = raw.trim().lowercase(Locale.ROOT)
                            if (w.length >= 2 && w.all { it.isLetter() || it == '-' || it == '\'' }) {
                                words.add(w)
                                count++
                                if (count >= 12000) break // limit to fast responsive set
                            }
                        }
                    }
                    if (words.size > 200) {
                        downloadedFromNet = true
                        _downloadStatus.value = DownloadStatus.Downloading(80, "Memproses ${words.size} kata...")
                        onProgress(80, "Memproses ${words.size} kata...")
                        break
                    }
                }
            } catch (e: Exception) {
                // Network unavailable or timed out; will fall back cleanly
                e.printStackTrace()
            }
        }

        // 2. If network was unreachable or empty, use comprehensive built-in master corpus
        if (words.size < 500) {
            _downloadStatus.value = DownloadStatus.Downloading(60, "Mengekstrak paket kamus lengkap offline...")
            onProgress(60, "Mengekstrak paket kamus...")
            val masterCorpus = when (language) {
                DictionaryLanguage.INDONESIAN -> IndonesianCorpus.words
                DictionaryLanguage.ENGLISH -> EnglishCorpus.words
            }
            words.addAll(masterCorpus)
        }

        // 3. Save to local storage file
        _downloadStatus.value = DownloadStatus.Downloading(90, "Menyimpan kamus ${language.displayName}...")
        onProgress(90, "Menyimpan ke perangkat...")

        val targetFile = getDictionaryFile(language)
        try {
            targetFile.bufferedWriter().use { writer ->
                for (w in words) {
                    writer.write(w)
                    writer.newLine()
                }
            }
            _downloadStatus.value = DownloadStatus.Success(language, words.size)
            onProgress(100, "Selesai (${words.size} kata)")
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            _downloadStatus.value = DownloadStatus.Error("Gagal menyimpan kamus: ${e.message}")
            return@withContext false
        }
    }

    fun resetStatus() {
        _downloadStatus.value = DownloadStatus.Idle
    }
}
