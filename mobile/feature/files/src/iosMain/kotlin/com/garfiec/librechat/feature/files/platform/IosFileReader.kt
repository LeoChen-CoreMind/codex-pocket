package com.garfiec.librechat.feature.files.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.posix.memcpy

class IosFileReader : FileReader {

    override fun readBytes(fileRef: Any): ByteArray? {
        val url = fileRef as? NSURL ?: return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return data.toByteArray()
    }

    override fun getFileName(fileRef: Any): String? {
        val url = fileRef as? NSURL ?: return null
        return url.lastPathComponent
    }

    override fun getMimeType(fileRef: Any): String? {
        val url = fileRef as? NSURL ?: return null
        val ext = url.pathExtension ?: return null
        return CommonMimeTypes.fromExtension(ext)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
