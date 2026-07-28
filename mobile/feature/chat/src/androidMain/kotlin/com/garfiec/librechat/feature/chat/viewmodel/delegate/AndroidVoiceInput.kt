package com.garfiec.librechat.feature.chat.viewmodel.delegate

/**
 * Android implementation of [PlatformVoiceInput] that wraps [VoiceInputDelegate].
 */
class AndroidVoiceInput(
    private val delegate: VoiceInputDelegate,
) : PlatformVoiceInput {
    override fun startRecording() = delegate.startRecording()
    override fun stopRecording() = delegate.stopRecording()
    override fun cancelRecording() = delegate.cancelRecording()
    override fun onDeviceSpeechResult(transcribedText: String) = delegate.onDeviceSpeechResult(transcribedText)
    override fun loadSpeechConfig() = delegate.loadSpeechConfig()
    override fun release() = delegate.release()
}
