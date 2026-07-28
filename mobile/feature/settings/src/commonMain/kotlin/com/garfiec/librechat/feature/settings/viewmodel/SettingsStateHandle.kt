package com.garfiec.librechat.feature.settings.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared state accessor passed to all SettingsViewModel delegates.
 * Provides atomic read/write access to the UI state and a coroutine scope.
 */
class SettingsStateHandle(
    val stateFlow: MutableStateFlow<SettingsUiState>,
    val scope: CoroutineScope,
) {
    val state: SettingsUiState get() = stateFlow.value

    /**
     * Atomic CAS-based state update. Uses [MutableStateFlow.update] under the hood
     * to avoid lost updates when multiple coroutines write concurrently.
     */
    fun update(transform: SettingsUiState.() -> SettingsUiState) {
        stateFlow.update { it.transform() }
    }
}
