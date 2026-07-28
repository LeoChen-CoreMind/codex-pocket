package com.garfiec.librechat.feature.files.platform

/**
 * Platform-abstracted file reader.
 * Android: ContentResolver + Uri
 * iOS: NSFileManager / PHAsset
 */
interface FileReader {
    /**
     * Reads file bytes from a platform-specific file reference.
     * @param fileRef Opaque platform reference (Android Uri, iOS URL)
     * @return file bytes, or null if unreadable
     */
    fun readBytes(fileRef: Any): ByteArray?

    /**
     * Resolves the display filename from a platform file reference.
     */
    fun getFileName(fileRef: Any): String?

    /**
     * Resolves the MIME type from a platform file reference.
     */
    fun getMimeType(fileRef: Any): String?
}
