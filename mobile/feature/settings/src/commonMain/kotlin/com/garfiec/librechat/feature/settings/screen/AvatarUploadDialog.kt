package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
