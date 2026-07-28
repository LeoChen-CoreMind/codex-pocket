package com.garfiec.librechat.feature.chat.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared state accessor passed to all ChatViewModel delegates.
 * Provides read/write access to the UI state and a coroutine scope.
 */
class ChatStateHandle(
    val stateFlow: MutableStateFlow<ChatUiState>,
    val scope: CoroutineScope,
) {
    val state: ChatUiState get() = stateFlow.value

    fun update(transform: ChatUiState.() -> ChatUiState) {
        stateFlow.update { it.transform() }
    }
}
