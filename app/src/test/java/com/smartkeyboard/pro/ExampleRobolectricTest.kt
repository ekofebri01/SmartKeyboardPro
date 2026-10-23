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
    fun verifyUpdateCheckerVersionComparison() {
        assertTrue(UpdateChecker.isVersionNewer("1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isVersionNewer("2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isVersionNewer("v1.0.1", "1.0.0"))
        assertFalse(UpdateChecker.isVersionNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isVersionNewer("1.0.0", "1.0.1"))
    }
}
