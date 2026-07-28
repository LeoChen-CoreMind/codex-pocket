package com.garfiec.librechat.feature.chat.viewmodel.delegate

/**
 * Platform-abstracted text-to-speech handling.
 * Android: wraps TextToSpeechDelegate with Android TTS engine + MediaPlayer.
 * iOS: no-op initially (TTS not yet supported on iOS).
 */
interface PlatformTts {
    fun readAloud(messageId: String)
    fun stopReading()
    fun maybeAutoReadResponse(responseText: String)
    fun release()
}
