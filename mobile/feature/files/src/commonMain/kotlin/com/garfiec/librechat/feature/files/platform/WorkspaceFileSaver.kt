package com.garfiec.librechat.feature.files.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberWorkspaceFileSaver(): (filename: String, mimeType: String, bytes: ByteArray) -> Unit
