package com.smartkeyboard.pro

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SignInScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var activeErrorInfo by remember { mutableStateOf<AuthErrorInfo?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B132B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Smart Keyboard Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Masuk dengan akun Google untuk mengaktifkan sinkronisasi otomatis kamus pribadi, kliping, dan backup ke Firestore Cloud.",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (errorMessage != null) {
                Surface(
                    color = Color(0xFF7F1D1D),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeErrorInfo != null) {
                                Button(
                                    onClick = { /* activeErrorInfo is shown via dialog */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("🛠️ Solusi & Cara Perbaiki", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                                            putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "Buka Pengaturan HP > Akun", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Text("⚙️ Akun HP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    signInWithGoogle(
                        context = context,
                        scope = coroutineScope,
                        onSuccess = {
                            isLoading = false
                            activeErrorInfo = null
                            Toast.makeText(context, "Berhasil masuk dengan Google!", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        },
                        onError = { msg, info ->
                            isLoading = false
                            errorMessage = msg
                            activeErrorInfo = info
                        }
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Masuk dengan Google", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text("Lanjutkan Tanpa Login (Lokal Sahaja)", color = Color(0xFF38BDF8), fontSize = 13.sp)
            }

            activeErrorInfo?.let { info ->
                AuthTroubleshooterDialog(
                    errorInfo = info,
                    onDismiss = { activeErrorInfo = null },
                    onRetryLogin = {
                        activeErrorInfo = null
                        isLoading = true
                        errorMessage = null
                        signInWithGoogle(
                            context = context,
                            scope = coroutineScope,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Berhasil masuk dengan Google!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            },
                            onError = { msg, errInfo ->
                                isLoading = false
                                errorMessage = msg
                                activeErrorInfo = errInfo
                            }
                        )
                    },
                    onContinueLocal = {
                        activeErrorInfo = null
                        onSkip()
                    }
                )
            }
        }
    }
}

fun signInWithGoogle(
    context: Context,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String, AuthErrorInfo?) -> Unit
) {
    AuthDiagnosticLogger.logAuthEvent("Initiating Google Sign-In Flow")

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        val errorInfo = AuthDiagnosticLogger.logAuthFailure(e, "Web Client ID Lookup", context)
        onError("Konfigurasi Google Sign-In tidak ditemukan (${errorInfo.code}).", errorInfo)
        return
    }

    if (clientId.isBlank()) {
        val e = IllegalStateException("default_web_client_id is empty")
        val errorInfo = AuthDiagnosticLogger.logAuthFailure(e, "Web Client ID Validation", context)
        onError("Web Client ID kosong (${errorInfo.code}).", errorInfo)
        return
    }

    val credentialManager = CredentialManager.create(context)
    val activity = context as? Activity
    if (activity == null) {
        val e = IllegalStateException("Context is not an Activity")
        val errorInfo = AuthDiagnosticLogger.logAuthFailure(e, "Activity Context Validation", context)
        onError("Activity context tidak valid (${errorInfo.code}).", errorInfo)
        return
    }

    val rawNonce = java.util.UUID.randomUUID().toString()
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
    val nonce = bytes.fold("") { str, it -> str + "%02x".format(it) }

    val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
        .setServerClientId(clientId)
        .setNonce(nonce)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            AuthDiagnosticLogger.logAuthEvent("Requesting credentials via GetGoogleIdOption", "Client ID: $clientId")
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                AuthDiagnosticLogger.logAuthEvent("Google ID Token received, signing into Firebase Auth")
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                AuthDiagnosticLogger.logAuthEvent("Firebase Auth Success", "User ID: ${authResult.user?.uid}, Email: ${authResult.user?.email}")
                onSuccess()
            } else {
                val err = IllegalArgumentException("Unexpected credential type: ${credential.type}")
                val errorInfo = AuthDiagnosticLogger.logAuthFailure(err, "Credential Type Check", context)
                onError("Tipe kredensial tidak sesuai (${errorInfo.code}: ${credential.type})", errorInfo)
            }
        } catch (e: GetCredentialCancellationException) {
            val errorInfo = AuthDiagnosticLogger.logAuthFailure(e, "GetGoogleIdOption User Cancelled", context)
            onError("${errorInfo.userFriendlyMessage} (${errorInfo.code})", errorInfo)
        } catch (e: Exception) {
            val primaryErrorInfo = AuthDiagnosticLogger.logAuthFailure(e, "GetGoogleIdOption Primary Flow Failure", context)
            AuthDiagnosticLogger.logAuthEvent("Primary flow failed (${primaryErrorInfo.code}), attempting GetSignInWithGoogleOption fallback...")

            try {
                val fallbackOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                    .setNonce(nonce)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder().addCredentialOption(fallbackOption).build()
                val result = credentialManager.getCredential(activity, fallbackRequest)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    AuthDiagnosticLogger.logAuthEvent("Fallback Google ID Token received, signing into Firebase Auth")
                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                    AuthDiagnosticLogger.logAuthEvent("Fallback Firebase Auth Success", "User ID: ${authResult.user?.uid}")
                    onSuccess()
                } else {
                    val err = IllegalArgumentException("Fallback credential type mismatch: ${credential.type}")
                    val errorInfo = AuthDiagnosticLogger.logAuthFailure(err, "Fallback Credential Type Check", context)
                    onError("Tipe kredensial fallback tidak sesuai (${errorInfo.code})", errorInfo)
                }
            } catch (e2: GetCredentialCancellationException) {
                val errorInfo = AuthDiagnosticLogger.logAuthFailure(e2, "Fallback User Cancelled", context)
                onError("${errorInfo.userFriendlyMessage} (${errorInfo.code})", errorInfo)
            } catch (e2: Exception) {
                val fallbackErrorInfo = AuthDiagnosticLogger.logAuthFailure(e2, "Fallback Flow Failure", context)
                onError("Gagal login Google [Code: ${fallbackErrorInfo.code}]. ${fallbackErrorInfo.userFriendlyMessage}", fallbackErrorInfo)
            }
        }
    }
}

fun signOutGoogle(
    context: Context,
    scope: CoroutineScope,
    onComplete: () -> Unit
) {
    Firebase.auth.signOut()
    scope.launch {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Clear credential state error", e)
        } finally {
            onComplete()
        }
    }
}
