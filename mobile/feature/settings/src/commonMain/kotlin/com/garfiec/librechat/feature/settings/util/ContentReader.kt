package com.garfiec.librechat.feature.settings.util

interface ContentReader {
    fun readBytes(uri: Any): ByteArray?
}
