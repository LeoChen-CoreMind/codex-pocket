package com.garfiec.librechat.feature.files.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFilePickerLauncher(
    onFilePick: (fileRef: Any) -> Unit,
): FilePickerLauncher {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            onFilePick(uri)
        }
    }
    return remember(launcher) {
        FilePickerLauncher(launchAction = { mimeType -> launcher.launch(mimeType) })
    }
}

actual class FilePickerLauncher(
    private val launchAction: (String) -> Unit,
) {
    actual fun launch(mimeType: String) {
        launchAction(mimeType)
    }
}
