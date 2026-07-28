package com.garfiec.librechat.feature.agents.util

/**
 * Reads bytes and MIME type from a platform-specific content URI.
 * On Android, wraps ContentResolver. On iOS, stubs (avatar picker is Android-only).
 */
interface ContentReader {
    fun readBytes(uri: Any): ByteArray?
    fun getMimeType(uri: Any): String?
    fun getFileName(uri: Any): String?
}
