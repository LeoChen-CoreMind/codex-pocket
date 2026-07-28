package com.garfiec.librechat.feature.agents.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class AndroidContentReader(private val context: Context) : ContentReader {

    override fun readBytes(uri: Any): ByteArray? {
        val androidUri = uri as? Uri ?: return null
        return context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
    }

    override fun getMimeType(uri: Any): String? {
        val androidUri = uri as? Uri ?: return null
        return context.contentResolver.getType(androidUri)
    }

    override fun getFileName(uri: Any): String? {
        val androidUri = uri as? Uri ?: return null
        context.contentResolver.query(androidUri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return androidUri.lastPathSegment
    }
}
