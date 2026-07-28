package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.runtime.Composable

/**
 * Copy text to the system clipboard.
 */
expect fun copyToClipboard(text: String, label: String = "")

/**
 * Show a short toast/notification to the user.
 */
expect fun showToast(message: String)

/**
 * Composable that provides a file-save callback.
 * When [triggerFileName] changes to a non-null value, the platform
 * prompts the user to save a file with that name and writes [content] to it.
 * Calls [onComplete] with success/failure message.
 */
@Composable
expect fun FileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
)
