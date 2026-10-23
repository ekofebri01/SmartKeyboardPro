package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val isNewer: Boolean,
    val publishedAt: String
)

sealed class UpdateResult {
    data class Success(val releaseInfo: ReleaseInfo) : UpdateResult()
    data class UpToDate(val currentVersion: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object UpdateChecker {
    private const val DEFAULT_OWNER = "ekofebri01"
    private const val DEFAULT_REPO = "SmartKeyboardPro"

    suspend fun checkLatestRelease(
        currentVersion: String,
        repoOwner: String = DEFAULT_OWNER,
        repoName: String = DEFAULT_REPO
    ): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "SmartKeyboardPro-Android")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseText = reader.use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "")
                val cleanLatest = tagName.removePrefix("v").removePrefix("V").trim()
                val cleanCurrent = currentVersion.removePrefix("v").removePrefix("V").trim()

                val title = json.optString("name", tagName)
                val body = json.optString("body", "No release notes provided.")
                val htmlUrl = json.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")
                val publishedAt = json.optString("published_at", "")

                // Find direct APK download url from assets if available
                var apkDownloadUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(cleanLatest, cleanCurrent)

                val info = ReleaseInfo(
                    tagName = tagName,
                    versionName = cleanLatest,
                    title = title,
                    releaseNotes = body,
                    downloadUrl = apkDownloadUrl,
                    htmlUrl = htmlUrl,
                    isNewer = isNewer,
                    publishedAt = publishedAt
                )

                if (isNewer) {
                    UpdateResult.Success(info)
                } else {
                    UpdateResult.UpToDate(currentVersion)
                }
            } else if (responseCode == 404) {
                UpdateResult.Error("No releases found on GitHub repository ($repoOwner/$repoName).")
            } else {
                UpdateResult.Error("GitHub API returned error code $responseCode.")
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to connect to GitHub.")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "SmartKeyboardPro-latest.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var currentUrl = downloadUrl
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "SmartKeyboardPro-Android")
                    connectTimeout = 12000
                    readTimeout = 20000
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == 307 || status == 308
                ) {
                    val newLocation = connection.getHeaderField("Location")
                    if (newLocation != null) {
                        currentUrl = newLocation
                        redirects++
                        connection.disconnect()
                        continue
                    }
                }
                break
            }

            val finalConnection = connection ?: throw IllegalStateException("Could not establish connection.")
            val totalBytes = finalConnection.contentLength.toLong()

            inputStream = finalConnection.inputStream
            outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8 * 1024)
            var downloadedBytes = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloadedBytes += read

                val progress = if (totalBytes > 0) {
                    downloadedBytes.toFloat() / totalBytes
                } else {
                    -1f
                }
                onProgress(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
            }

            outputStream.flush()
            onProgress(DownloadState.Completed(apkFile))

        } catch (e: Exception) {
            e.printStackTrace()
            onProgress(DownloadState.Error(e.message ?: "Download failed"))
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false

            // Check if unknown sources install permission is required (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun cleanupUpdateFiles(context: Context) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (updateDir.exists()) {
                updateDir.listFiles()?.forEach { file ->
                    file.delete()
                }
                updateDir.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        } catch (e: Exception) {
            return latest != current && latest.isNotEmpty()
        }
    }

    fun openBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
