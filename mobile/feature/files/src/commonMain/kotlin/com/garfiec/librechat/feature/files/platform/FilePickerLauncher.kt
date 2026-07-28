package com.garfiec.librechat.feature.files.platform

import androidx.compose.runtime.Composable

/**
 * Platform file picker composable.
 * When [launch] is called, shows the platform's native file picker.
 * On file selection, calls [onFilePick] with a platform-specific file reference
 * (Android Uri, iOS NSURL).
 */
@Composable
expect fun rememberFilePickerLauncher(
    onFilePick: (fileRef: Any) -> Unit,
): FilePickerLauncher

expect class FilePickerLauncher {
    fun launch(mimeType: String)
}
