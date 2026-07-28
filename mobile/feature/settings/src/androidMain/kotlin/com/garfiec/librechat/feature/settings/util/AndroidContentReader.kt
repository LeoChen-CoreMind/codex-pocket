package com.garfiec.librechat.feature.settings.util

import android.content.Context
import android.net.Uri

class AndroidContentReader(private val context: Context) : ContentReader {
    override fun readBytes(uri: Any): ByteArray? {
        val androidUri = uri as? Uri ?: return null
        return context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
    }
}
