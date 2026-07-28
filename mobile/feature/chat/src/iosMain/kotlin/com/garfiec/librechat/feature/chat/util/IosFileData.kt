package com.garfiec.librechat.feature.chat.util

/**
 * Wrapper for general file data from iOS sources (document picker, etc.).
 * For non-image files. Passed through [PlatformFileHandler.onFilesSelected].
 */
class IosFileData(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
)
