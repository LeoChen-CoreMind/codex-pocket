package com.garfiec.librechat.feature.agents.di

import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.agents.components.PreloadedFileRef
import com.garfiec.librechat.feature.agents.util.ContentReader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.posix.memcpy

actual val agentsPlatformModule: Module = module {
    single {
        @OptIn(ExperimentalForeignApi::class)
        object : ContentReader {
            override fun readBytes(uri: Any): ByteArray? {
                // AgentFilePicker pre-reads bytes inside its delegate where
                // the security-scoped resource is guaranteed valid; we just
                // hand them back here. NSURL handling stays as a fallback
                // for code paths that haven't been migrated.
                if (uri is PreloadedFileRef) return uri.bytes
                val nsUrl = uri as? NSURL
                if (nsUrl == null) {
                    Logger.w("AgentsContentReader") { "readBytes called with non-NSURL: ${uri::class}" }
                    return null
                }
                val data = NSData.dataWithContentsOfURL(nsUrl)
                if (data == null) {
                    Logger.w("AgentsContentReader") { "Failed to read data from: $nsUrl" }
                    return null
                }
                val size = data.length.toInt()
                if (size == 0) return ByteArray(0)
                val result = ByteArray(size)
                result.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
                return result
            }

            override fun getMimeType(uri: Any): String? {
                if (uri is PreloadedFileRef) return uri.mimeType
                val nsUrl = uri as? NSURL ?: return null
                val ext = nsUrl.pathExtension?.lowercase() ?: return null
                return mimeTypeFromExtension(ext)
            }

            override fun getFileName(uri: Any): String? {
                if (uri is PreloadedFileRef) return uri.filename
                val nsUrl = uri as? NSURL ?: return null
                return nsUrl.lastPathComponent
            }
        }
    } bind ContentReader::class
}

private fun mimeTypeFromExtension(ext: String): String? = when (ext) {
    "json" -> "application/json"
    "yaml", "yml" -> "application/x-yaml"
    "txt" -> "text/plain"
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "mp4" -> "video/mp4"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    "xml" -> "application/xml"
    "zip" -> "application/zip"
    "csv" -> "text/csv"
    "md" -> "text/markdown"
    else -> null
}
