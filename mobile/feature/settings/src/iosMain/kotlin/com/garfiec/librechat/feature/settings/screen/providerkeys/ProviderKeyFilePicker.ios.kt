package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.runtime.Composable

// iOS service-account file picker not implemented — Android-only path. The Google form's
// always-visible paste-textarea is the iOS fallback.
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun rememberProviderKeyFilePicker(
    onFileRead: (jsonContents: String?) -> Unit,
): (() -> Unit)? = null
