package com.smartkeyboard.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun verifyKeyboardModeEnum() {
        assertEquals("ALPHA", KeyboardMode.ALPHA.name)
        assertEquals("NUMBERS", KeyboardMode.NUMBERS.name)
        assertEquals("SYMBOLS", KeyboardMode.SYMBOLS.name)
        assertEquals("EMOJI", KeyboardMode.EMOJI.name)
    }

    @Test
    fun verifyPredefinedThemesAvailable() {
        val themes = ThemeManager.predefinedThemes
        assertTrue(themes.isNotEmpty())
        assertTrue(themes.any { it.id == "midnight" })
        assertTrue(themes.any { it.id == "cyberpunk" })
        assertTrue(themes.any { it.id == "emerald" })
    }

    @Test
    fun verifyThemeConfigLayoutDefaults() {
        val defaultConfig = ThemeConfig()
        assertTrue(defaultConfig.showNumberRow)
        assertTrue(defaultConfig.showKeySymbols)
        assertTrue(defaultConfig.enableLongPressSymbols)
        assertEquals(44, defaultConfig.keyHeightDp)
        assertEquals("rounded", defaultConfig.keyShapeStyle)
    }

    @Test
    fun verifyUpdateCheckerVersionComparison() {
        assertTrue(UpdateChecker.isVersionNewer("1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isVersionNewer("v1.0.1", "1.0.0"))
        assertFalse(UpdateChecker.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isVersionNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun verifyAuthDiagnosticLoggerCancellationMapping() {
        val cancellationEx = androidx.credentials.exceptions.GetCredentialCancellationException("User closed picker")
        val info = AuthDiagnosticLogger.logAuthFailure(cancellationEx, "Test Cancellation")
        assertEquals(AuthDiagnosticLogger.CODE_SIGN_IN_CANCELLED, info.code)
        assertNotNull(info.userFriendlyMessage)
        assertNotNull(info.recommendedAction)
    }

    @Test
    fun verifyAuthDiagnosticLoggerDeveloperErrorMapping() {
        val apiEx = com.google.android.gms.common.api.ApiException(
            com.google.android.gms.common.api.Status(com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR, "SHA1 mismatch")
        )
        val info = AuthDiagnosticLogger.logAuthFailure(apiEx, "Test Developer Error")
        assertEquals(AuthDiagnosticLogger.CODE_DEVELOPER_ERROR_SHA1_MISMATCH, info.code)
        assertTrue(info.recommendedAction.contains("SHA-1"))
    }

    @Test
    fun verifyAuthDiagnosticLoggerNetworkErrorMapping() {
        val networkEx = com.google.firebase.FirebaseNetworkException("No connection")
        val info = AuthDiagnosticLogger.logAuthFailure(networkEx, "Test Network Error")
        assertEquals(AuthDiagnosticLogger.CODE_NETWORK_ERROR, info.code)
    }

    @Test
    fun verifyAutoTextEntryModelAndParsing() {
        val entry = AutoTextEntry(id = "123", shortcut = "thx", fullText = "Terima kasih banyak!")
        val jsonObj = entry.toJsonObject()
        assertEquals("123", jsonObj.getString("id"))
        assertEquals("thx", jsonObj.getString("shortcut"))
        assertEquals("Terima kasih banyak!", jsonObj.getString("fullText"))

        val parsed = AutoTextEntry.fromJsonObject(jsonObj)
        assertEquals("123", parsed.id)
        assertEquals("thx", parsed.shortcut)
        assertEquals("Terima kasih banyak!", parsed.fullText)
    }

    @Test
    fun verifyAutoTextEntryLegacyStringConversion() {
        val legacyColon = AutoTextEntry.createFromLegacyString("alamat: Jl. Sudirman No. 123")
        assertEquals("alamat", legacyColon.shortcut)
        assertEquals("Jl. Sudirman No. 123", legacyColon.fullText)

        val legacyPlain = AutoTextEntry.createFromLegacyString("Terima kasih atas infonya!")
        assertEquals("terima", legacyPlain.shortcut)
        assertEquals("Terima kasih atas infonya!", legacyPlain.fullText)
    }
}
