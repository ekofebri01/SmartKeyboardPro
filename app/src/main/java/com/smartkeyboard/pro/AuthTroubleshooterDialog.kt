package com.smartkeyboard.pro

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthTroubleshooterDialog(
    errorInfo: AuthErrorInfo,
    onDismiss: () -> Unit,
    onRetryLogin: () -> Unit,
    onContinueLocal: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showRawDetails by remember { mutableStateOf(false) }

    val badgeColor = when (errorInfo.code) {
        AuthDiagnosticLogger.CODE_SIGN_IN_CANCELLED -> Color(0xFFF59E0B)
        AuthDiagnosticLogger.CODE_NO_CREDENTIAL_FOUND -> Color(0xFFEAB308)
        AuthDiagnosticLogger.CODE_DEVELOPER_ERROR_SHA1_MISMATCH -> Color(0xFFEF4444)
        AuthDiagnosticLogger.CODE_NETWORK_ERROR -> Color(0xFF3B82F6)
        AuthDiagnosticLogger.CODE_INVALID_CREDENTIALS -> Color(0xFFA855F7)
        else -> Color(0xFFEC4899)
    }

    val badgeText = when (errorInfo.code) {
        AuthDiagnosticLogger.CODE_SIGN_IN_CANCELLED -> "🛑 Login Dibatalkan / Ditutup"
        AuthDiagnosticLogger.CODE_NO_CREDENTIAL_FOUND -> "⚠️ Akun Google Belum Ada"
        AuthDiagnosticLogger.CODE_DEVELOPER_ERROR_SHA1_MISMATCH -> "🔧 SHA-1 / GMS Mismatch"
        AuthDiagnosticLogger.CODE_NETWORK_ERROR -> "🌐 Koneksi Terputus"
        AuthDiagnosticLogger.CODE_INVALID_CREDENTIALS -> "🔑 Kredensial Tidak Valid"
        else -> "🔐 Auth Error: ${errorInfo.code}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "🛠️ Asisten Deteksi & Perbaiki Login",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Ringkasan Penyebab Gagal
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📌 Penyebab Gagal Login:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorInfo.summary,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }

                // Section 2: Panduan Langkah Perbaikan
                Surface(
                    color = Color(0xFF065F46).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💡 Panduan Cara Memperbaiki:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorInfo.recommendedAction,
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Section 3: Aksi Solusi yang Disesuaikan dengan Diagnosa Real-Time
                Text(
                    text = "👇 Solusi Spesifik (Sesuai Deteksi Real-Time):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                when (errorInfo.code) {
                    AuthDiagnosticLogger.CODE_NO_CREDENTIAL_FOUND, AuthDiagnosticLogger.CODE_SIGN_IN_CANCELLED -> {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        Toast.makeText(context, "Buka Pengaturan HP > Akun", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚙️ Buka Pengaturan Akun HP (Tambah Akun Google)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthDiagnosticLogger.CODE_DEVELOPER_ERROR_SHA1_MISMATCH -> {
                        val pkgName = context.packageName
                        val infoStr = "Package: $pkgName\nSHA-1 Upload: 43:F1:80:B5:E5:B3:97:FF:8A:F4:5E:59:68:0F:B1:7A:F0:EB:6E:D9\nSHA-1 Debug: F3:DC:B2:FD:47:F3:31:2B:E3:AD:E7:8E:30:01:1D:39:CD:D0:93:1F"
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(infoStr))
                                Toast.makeText(context, "Package & SHA-1 disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📋 Salin Package Name & Fingerprint SHA-1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthDiagnosticLogger.CODE_NETWORK_ERROR -> {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Periksa koneksi Wi-Fi / Data Seluler", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📶 Buka Pengaturan Jaringan Internet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthDiagnosticLogger.CODE_INVALID_CREDENTIALS -> {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cek Pengaturan Jam HP", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⏰ Buka Pengaturan Tanggal & Jam HP", fontSize = 12.sp, color = Color(0xFF38BDF8))
                        }
                    }

                    else -> {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Buka Pengaturan Sinkronisasi HP", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚙️ Periksa Sinkronisasi Akun Android", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Button Utama: Coba Ulang Login Google
                Button(
                    onClick = {
                        onDismiss()
                        onRetryLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Coba Ulang Login Google Sekarang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Section 4: Log Diagnosa & Detail Teknikal
                TextButton(
                    onClick = { showRawDetails = !showRawDetails },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (showRawDetails) "🔼 Sembunyikan Log Technical" else "🔽 Lihat Detail Technical Logcat",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                }

                if (showRawDetails) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = errorInfo.technicalDetails,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val fullReport = AuthDiagnosticLogger.latestReport.value ?: errorInfo.technicalDetails
                            clipboardManager.setText(AnnotatedString(fullReport))
                            Toast.makeText(context, "Log diagnosa disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📄 Salin Log Technical ke Clipboard", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onContinueLocal()
            }) {
                Text("Gunakan Offline / Lokal", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
