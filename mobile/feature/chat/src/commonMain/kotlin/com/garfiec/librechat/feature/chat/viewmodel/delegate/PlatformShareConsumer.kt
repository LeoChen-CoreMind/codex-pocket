package com.garfiec.librechat.feature.chat.viewmodel.delegate

import kotlinx.coroutines.flow.SharedFlow

/**
 * Platform-abstracted share intent consumer.
 * Android: wraps ShareIntentConsumer (share sheet intents).
 * iOS: no-op (iOS share extensions handled differently).
 */
interface PlatformShareConsumer {
    val shareAvailable: SharedFlow<Unit>
    fun consume(): ShareData?
}

data class ShareData(
    val text: String? = null,
    val fileRefs: List<Any> = emptyList(),
)
