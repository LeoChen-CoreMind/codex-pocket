package com.garfiec.librechat.feature.files.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberWorkspaceFileSaver(): (String, String, ByteArray) -> Unit = { _, _, _ -> }
