package com.garfiec.librechat.feature.auth.screen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Custom password mask that avoids [androidx.compose.ui.text.input.PasswordVisualTransformation].
 * CMP on iOS detects PasswordVisualTransformation and sets isSecureTextEntry on the native
 * UITextField, which can cause a phantom pre-filled character from iOS keychain autofill.
 * This transformation performs identical masking without triggering that platform behavior.
 */
private const val MASK_CHAR = '\u2022'

fun passwordMaskTransformation(): VisualTransformation = VisualTransformation { text ->
    TransformedText(
        AnnotatedString(MASK_CHAR.toString().repeat(text.length)),
        OffsetMapping.Identity,
    )
}
