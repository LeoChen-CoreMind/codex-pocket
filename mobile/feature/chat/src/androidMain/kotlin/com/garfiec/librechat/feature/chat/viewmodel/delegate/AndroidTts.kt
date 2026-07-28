package com.garfiec.librechat.feature.chat.viewmodel.delegate

/**
 * Android implementation of [PlatformTts] that wraps [TextToSpeechDelegate].
 */
class AndroidTts(
    private val delegate: TextToSpeechDelegate,
) : PlatformTts {
    override fun readAloud(messageId: String) = delegate.readAloud(messageId)
    override fun stopReading() = delegate.stopReading()
    override fun maybeAutoReadResponse(responseText: String) = delegate.maybeAutoReadResponse(responseText)
    override fun release() = delegate.release()
}
