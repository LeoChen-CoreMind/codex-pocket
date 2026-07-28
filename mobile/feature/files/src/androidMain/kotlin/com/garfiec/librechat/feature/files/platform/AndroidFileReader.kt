package com.garfiec.librechat.feature.files.platform

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns

class AndroidFileReader(
    private val context: Application,
) : FileReader {

    override fun readBytes(fileRef: Any): ByteArray? {
        val uri = fileRef as? Uri ?: return null
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    override fun getFileName(fileRef: Any): String? {
        val uri = fileRef as? Uri ?: return null
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else {
                null
            }
        }
    }

    override fun getMimeType(fileRef: Any): String? {
        val uri = fileRef as? Uri ?: return null
        return context.contentResolver.getType(uri)
            ?: uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotEmpty() }
                ?.let { CommonMimeTypes.fromExtension(it) }
    }
}
