package com.smartkeyboard.pro

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Decides whether the currently focused editor is a sensitive field.
 *
 * Password / PIN fields, editors that opt out of personalized learning and editors
 * without a declared input type must never feed the personal dictionary, the suggestion
 * bar, auto-text expansion or the cloud sync.
 *
 * The rules live here (instead of inside [SmartKeyboardIME]) so they can be unit tested
 * without starting an input method service.
 */
object InputFieldClassifier {

    fun isSensitiveField(info: EditorInfo?): Boolean {
        // No editor info means we cannot prove the field is safe: treat it as sensitive.
        if (info == null) return true
        return isSensitive(inputType = info.inputType, imeOptions = info.imeOptions)
    }

    fun isSensitive(inputType: Int, imeOptions: Int): Boolean {
        val noPersonalizedLearning =
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        if (noPersonalizedLearning) return true

        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            InputType.TYPE_NULL -> true
            else -> false
        }
    }
}
