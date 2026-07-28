package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.ShareIntentConsumer
import kotlinx.coroutines.flow.SharedFlow

/**
 * Android implementation of [PlatformShareConsumer] wrapping [ShareIntentConsumer].
 */
class AndroidShareConsumer : PlatformShareConsumer {
    override val shareAvailable: SharedFlow<Unit> = ShareIntentConsumer.shareAvailable

    override fun consume(): ShareData? {
        val data = ShareIntentConsumer.consume() ?: return null
        return ShareData(
            text = data.text,
            fileRefs = data.fileUris,
        )
    }
}
