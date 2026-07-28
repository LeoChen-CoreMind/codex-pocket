package com.garfiec.librechat.feature.chat.util

/**
 * Wrapper for image data from iOS sources (clipboard, photo picker, etc.).
 * Passed as the platform reference through [PlatformFileHandler.onFilesSelected].
 */
class IosImageData(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
)
