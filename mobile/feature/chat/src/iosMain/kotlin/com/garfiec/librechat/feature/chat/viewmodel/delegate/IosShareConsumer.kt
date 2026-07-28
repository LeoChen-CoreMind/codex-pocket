package com.garfiec.librechat.feature.chat.viewmodel.delegate

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * iOS no-op implementation of [PlatformShareConsumer].
 * iOS doesn't use Android share intents.
 */
class IosShareConsumer : PlatformShareConsumer {
    override val shareAvailable: SharedFlow<Unit> = MutableSharedFlow()
    override fun consume(): ShareData? = null
}
