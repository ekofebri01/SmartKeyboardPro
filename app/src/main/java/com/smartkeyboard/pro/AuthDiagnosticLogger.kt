package com.smartkeyboard.pro

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnostic data class representing a categorized Auth error.
 */
data class AuthErrorInfo(
    val code: String,
    val summary: String,
    val userFriendlyMessage: String,
    val recommendedAction: String,
    val technicalDetails: String
)

/**
 * Diagnostic logging utility for Firebase Auth and CredentialManager flows.
 * Captures, categorizes, and formats detailed error codes into Logcat for easy tracing.
 */
object AuthDiagnosticLogger {
    private const val TAG = "AuthDiagnostics"

    // Standard Diagnostic Error Codes
    const val CODE_SIGN_IN_CANCELLED = "SIGN_IN_CANCELLED"
    const val CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val CODE_DEVELOPER_ERROR_SHA1_MISMATCH = "DEVELOPER_ERROR_SHA1_MISMATCH"
    const val CODE_NO_CREDENTIAL_FOUND = "NO_CREDENTIAL_FOUND"
    const val CODE_USER_DISABLED = "USER_DISABLED"
    const val CODE_NETWORK_ERROR = "NETWORK_ERROR"
    const val CODE_MISSING_CLIENT_ID = "MISSING_CLIENT_ID"
    const val CODE_CREDENTIAL_TYPE_MISMATCH = "CREDENTIAL_TYPE_MISMATCH"
    const val CODE_FIREBASE_AUTH_ERROR = "FIREBASE_AUTH_ERROR"
    const val CODE_UNKNOWN_ERROR = "UNKNOWN_AUTH_ERROR"

    private val _latestReport = MutableStateFlow<String?>(null)
    val latestReport: StateFlow<String?> = _latestReport.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    /**
     * Categorizes a Throwable, prints structured diagnostic logs to Logcat, and returns [AuthErrorInfo].
     */
    fun logAuthFailure(
        throwable: Throwable,
        contextName: String = "Google Sign-In Flow",
        context: Context? = null
    ): AuthErrorInfo {
        val errorInfo = analyzeException(throwable)

        val logBlock = StringBuilder().apply {
            append("\n==================== [AUTH DIAGNOSTIC FAILURE REPORT] ====================\n")
            append("Context            : $contextName\n")
            append("Error Code         : ${errorInfo.code}\n")
            append("Summary            : ${errorInfo.summary}\n")
            append("Exception Class    : ${throwable.javaClass.name}\n")
            append("Raw Exception Msg  : ${throwable.message ?: "N/A"}\n")
            if (throwable is FirebaseAuthException) {
                append("Firebase Auth Code : ${throwable.errorCode}\n")
            }
            if (throwable is ApiException) {
                append("GMS Status Code    : ${throwable.statusCode} (${CommonStatusCodes.getStatusCodeString(throwable.statusCode)})\n")
            }
            append("Recommended Action : ${errorInfo.recommendedAction}\n")
            append("User Friendly Msg  : ${errorInfo.userFriendlyMessage}\n")
            append("--------------------------------------------------------------------------\n")
            append("Stack Trace Snippet:\n")
            val stackSnippet = throwable.stackTrace.take(6).joinToString("\n") { "  at $it" }
            append(stackSnippet)
            append("\n===========================================================================")
        }.toString()

        _latestReport.value = logBlock
        _logEntries.value = (_logEntries.value + logBlock).takeLast(20)

        when (errorInfo.code) {
            CODE_SIGN_IN_CANCELLED -> Log.w(TAG, logBlock)
            CODE_DEVELOPER_ERROR_SHA1_MISMATCH, CODE_INVALID_CREDENTIALS, CODE_FIREBASE_AUTH_ERROR -> Log.e(TAG, logBlock, throwable)
            else -> Log.e(TAG, logBlock, throwable)
        }

        return errorInfo
    }

    /**
     * Logs general Auth lifecycle events (e.g., initiation, token verification).
     */
    fun logAuthEvent(event: String, details: String = "") {
        val line = "🟢 [AUTH EVENT] $event ${if (details.isNotEmpty()) "- $details" else ""}"
        Log.i(TAG, line)
        _logEntries.value = (_logEntries.value + line).takeLast(20)
    }

    /**
     * Analyzes exception instance and maps to specific AuthErrorInfo.
     */
    private fun analyzeException(e: Throwable): AuthErrorInfo {
        return when (e) {
            is GetCredentialCancellationException -> AuthErrorInfo(
                code = CODE_SIGN_IN_CANCELLED,
                summary = "Proses login dibatalkan oleh pengguna atau dialog Google Sign-In ditutup sebelum memilih akun.",
                userFriendlyMessage = "Proses masuk Google dibatalkan.",
                recommendedAction = "Pastikan HP/Emulator sudah menambahkan Akun Google (Settings > Accounts) dan pilih salah satu akun saat dialog muncul.",
                technicalDetails = "GetCredentialCancellationException: ${e.message}"
            )

            is NoCredentialException -> AuthErrorInfo(
                code = CODE_NO_CREDENTIAL_FOUND,
                summary = "Tidak ada akun Google yang terdaftar di perangkat / Play Services.",
                userFriendlyMessage = "Tidak ada akun Google terdaftar di HP/Emulator ini.",
                recommendedAction = "Tambahkan akun Google di Pengaturan Android > Akun (Settings > Accounts) lalu coba lagi.",
                technicalDetails = "NoCredentialException: ${e.message}"
            )

            is GetCredentialProviderConfigurationException -> AuthErrorInfo(
                code = CODE_DEVELOPER_ERROR_SHA1_MISMATCH,
                summary = "Konfigurasi Google Play Services atau Web Client ID tidak cocok.",
                userFriendlyMessage = "Konfigurasi Google Play Services tidak valid.",
                recommendedAction = "Periksa SHA-1 fingerprint dan Package Name di Firebase Console & google-services.json.",
                technicalDetails = "GetCredentialProviderConfigurationException: ${e.message}"
            )

            is GetCredentialException -> {
                val message = e.message ?: ""
                if (message.contains("16:") || message.contains("12501") || message.contains("Canceled")) {
                    AuthErrorInfo(
                        code = CODE_SIGN_IN_CANCELLED,
                        summary = "Dialog login Google dibatalkan atau ditutup.",
                        userFriendlyMessage = "Proses masuk Google dibatalkan.",
                        recommendedAction = "Ulangi proses login dan pilih salah satu akun Google yang aktif.",
                        technicalDetails = "GetCredentialException (Cancelled): $message"
                    )
                } else {
                    AuthErrorInfo(
                        code = CODE_INVALID_CREDENTIALS,
                        summary = "Gagal mengambil kredensial Google dari CredentialManager.",
                        userFriendlyMessage = "Gagal memproses kredensial Google.",
                        recommendedAction = "Periksa Google Play Services & Web Client ID di firebase_applet_config.",
                        technicalDetails = "GetCredentialException: $message"
                    )
                }
            }

            is FirebaseAuthInvalidCredentialsException -> AuthErrorInfo(
                code = CODE_INVALID_CREDENTIALS,
                summary = "Token ID Google atau kredensial yang dikirim ke Firebase Auth tidak valid.",
                userFriendlyMessage = "Kredensial login Google tidak valid.",
                recommendedAction = "Verifikasi Web Client ID server dan pastikan jam/sistem perangkat sinkron.",
                technicalDetails = "FirebaseAuthInvalidCredentialsException: errorCode=${e.errorCode}, msg=${e.message}"
            )

            is FirebaseAuthInvalidUserException -> AuthErrorInfo(
                code = CODE_USER_DISABLED,
                summary = "Akun pengguna telah dinonaktifkan atau dihapus di Firebase Auth Console.",
                userFriendlyMessage = "Akun ini telah dinonaktifkan.",
                recommendedAction = "Aktifkan kembali akun di Firebase Console atau gunakan akun Google lain.",
                technicalDetails = "FirebaseAuthInvalidUserException: errorCode=${e.errorCode}, msg=${e.message}"
            )

            is FirebaseAuthException -> AuthErrorInfo(
                code = CODE_FIREBASE_AUTH_ERROR,
                summary = "Kesalahan Firebase Auth dengan kode: ${e.errorCode}",
                userFriendlyMessage = "Gagal autentikasi Firebase (${e.errorCode}).",
                recommendedAction = "Cek konfigurasi Firebase Authentication di console.",
                technicalDetails = "FirebaseAuthException: errorCode=${e.errorCode}, msg=${e.message}"
            )

            is FirebaseNetworkException -> AuthErrorInfo(
                code = CODE_NETWORK_ERROR,
                summary = "Tidak ada koneksi internet saat menghubungi Firebase Auth server.",
                userFriendlyMessage = "Gagal terhubung ke jaringan internet.",
                recommendedAction = "Pastikan koneksi Wi-Fi / Data Seluler terhubung dengan lancar.",
                technicalDetails = "FirebaseNetworkException: ${e.message}"
            )

            is ApiException -> {
                when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR -> AuthErrorInfo(
                        code = CODE_DEVELOPER_ERROR_SHA1_MISMATCH,
                        summary = "DEVELOPER_ERROR (Code 10): Mismatch SHA-1 fingerprint atau Package Name.",
                        userFriendlyMessage = "Konfigurasi developer mismatch (DEVELOPER_ERROR).",
                        recommendedAction = "Tambahkan SHA-1 sertifikat APK/Keystore ke Firebase Console & download google-services.json baru.",
                        technicalDetails = "ApiException status 10 (DEVELOPER_ERROR)"
                    )
                    CommonStatusCodes.CANCELED, 12501 -> AuthErrorInfo(
                        code = CODE_SIGN_IN_CANCELLED,
                        summary = "Sign in dibatalkan oleh user atau sistem.",
                        userFriendlyMessage = "Proses masuk Google dibatalkan.",
                        recommendedAction = "Ulangi proses dan selesaikan dialog login.",
                        technicalDetails = "ApiException status ${e.statusCode} (CANCELED)"
                    )
                    CommonStatusCodes.NETWORK_ERROR -> AuthErrorInfo(
                        code = CODE_NETWORK_ERROR,
                        summary = "Network error saat menghubungi Play Services.",
                        userFriendlyMessage = "Gagal terhubung ke Google Play Services.",
                        recommendedAction = "Periksa jaringan internet.",
                        technicalDetails = "ApiException status NETWORK_ERROR"
                    )
                    else -> AuthErrorInfo(
                        code = CODE_INVALID_CREDENTIALS,
                        summary = "API Exception Google Play Services code: ${e.statusCode}",
                        userFriendlyMessage = "Kesalahan Google Play Services (${e.statusCode}).",
                        recommendedAction = "Pastikan Google Play Services versi terbaru terpasang.",
                        technicalDetails = "ApiException: statusCode=${e.statusCode}, msg=${e.message}"
                    )
                }
            }

            else -> AuthErrorInfo(
                code = CODE_UNKNOWN_ERROR,
                summary = "Kesalahan autentikasi yang tidak terduga: ${e.javaClass.simpleName}",
                userFriendlyMessage = e.localizedMessage ?: "Gagal masuk dengan Google.",
                recommendedAction = "Cek logcat untuk detail stack trace lengkap.",
                technicalDetails = "${e.javaClass.name}: ${e.message}"
            )
        }
    }
}
