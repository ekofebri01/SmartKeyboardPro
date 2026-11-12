package com.smartkeyboard.pro

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the sensitive-field rules that decide whether the IME may learn, suggest,
 * auto-expand or cloud-sync what the user types.
 */
class InputFieldClassifierTest {

    private fun textField(vararg variations: Int): Int =
        variations.fold(InputType.TYPE_CLASS_TEXT) { acc, variation -> acc or variation }

    @Test
    fun `password variations are sensitive`() {
        val passwordVariations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        passwordVariations.forEach { variation ->
            assertTrue(
                "variation $variation must be sensitive",
                InputFieldClassifier.isSensitive(textField(variation), 0)
            )
        }
    }

    @Test
    fun `ordinary text fields are not sensitive`() {
        assertFalse(InputFieldClassifier.isSensitive(InputType.TYPE_CLASS_TEXT, 0))
        assertFalse(
            InputFieldClassifier.isSensitive(
                textField(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                0
            )
        )
        assertFalse(
            InputFieldClassifier.isSensitive(textField(InputType.TYPE_TEXT_VARIATION_URI), 0)
        )
        assertFalse(
            InputFieldClassifier.isSensitive(
                textField(InputType.TYPE_TEXT_FLAG_MULTI_LINE),
                0
            )
        )
    }

    @Test
    fun `numeric password variation is sensitive but plain numbers are not`() {
        assertTrue(
            InputFieldClassifier.isSensitive(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                0
            )
        )
        assertFalse(InputFieldClassifier.isSensitive(InputType.TYPE_CLASS_NUMBER, 0))
        assertFalse(InputFieldClassifier.isSensitive(InputType.TYPE_CLASS_PHONE, 0))
    }

    @Test
    fun `editors opting out of personalized learning are sensitive`() {
        assertTrue(
            InputFieldClassifier.isSensitive(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
    }

    @Test
    fun `undeclared input type and missing editor info are treated as sensitive`() {
        assertTrue(InputFieldClassifier.isSensitive(InputType.TYPE_NULL, 0))
        assertTrue(InputFieldClassifier.isSensitiveField(null))
    }
}
