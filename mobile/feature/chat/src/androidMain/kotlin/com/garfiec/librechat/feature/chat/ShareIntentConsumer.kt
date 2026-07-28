package com.garfiec.librechat.feature.chat

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data representing content shared to the app via Android share intents.
 *
 * @param text Shared text content (from EXTRA_TEXT). Pre-filled in the message input.
 * @param fileUris URIs of shared files/images (from EXTRA_STREAM). Attached as pending files.
 */
data class SharedContent(
    val text: String? = null,
    val fileUris: List<Uri> = emptyList(),
)

/**
 * Singleton that holds pending shared content from Android share intents.
 *
 * The app module's MainActivity writes to this when it receives a share intent,
 * and ChatViewModel reads from it when initializing a new chat.
 * The data is cleared after consumption to prevent re-processing.
 *
 * This lives in the feature:chat module (rather than app) so that ChatViewModel
 * can access it without a circular dependency. The app module writes to it
 * via the public [setPendingShare] method.
 *
 * Active ChatViewModels observe [shareAvailable] to reactively consume shared
 * content when the user is already on a chat screen (instead of always forcing
 * navigation to a new chat).
 */
object ShareIntentConsumer {

    @Volatile
    private var pendingShare: SharedContent? = null

    /**
     * Emits a Unit each time new shared content is set, allowing active
     * ChatViewModels to reactively consume it without polling.
     */
    private val _shareAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shareAvailable: SharedFlow<Unit> = _shareAvailable.asSharedFlow()

    /**
     * Sets pending shared content. Called by MainActivity when a share intent arrives.
     * Emits on [shareAvailable] so any active ChatViewModel can pick it up.
     */
    fun setPendingShare(data: SharedContent) {
        pendingShare = data
        _shareAvailable.tryEmit(Unit)
    }

    /**
     * Atomically retrieves and clears the pending shared content.
     * Returns null if no share data is pending.
     */
    fun consume(): SharedContent? {
        val data = pendingShare
        pendingShare = null
        return data
    }
}
