package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.network.api.VsCodeApi
import com.garfiec.librechat.core.network.api.BridgeEventsApi
import com.garfiec.librechat.core.network.api.VsCodeInstanceDto
import com.garfiec.librechat.core.network.api.OnlineConversationDto
import kotlinx.coroutines.delay
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

data class VsCodeBindingUiState(
    val loading: Boolean = false,
    val instances: List<VsCodeInstanceDto> = emptyList(),
    val boundInstanceId: String? = null,
    val onlineConversations: List<OnlineConversationDto> = emptyList(),
    val closingConversationKeys: Set<String> = emptySet(),
    val error: String? = null,
    val scopeRevision: Int = 0,
) {
    val boundInstance: VsCodeInstanceDto?
        get() = instances.firstOrNull { it.instanceId == boundInstanceId }
}

class VsCodeBindingViewModel(
    private val api: VsCodeApi,
    private val bridgeEventsApi: BridgeEventsApi,
    private val conversationRepository: ConversationRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VsCodeBindingUiState(loading = true))
    val uiState: StateFlow<VsCodeBindingUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeWindowChanges()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            refreshAllNow()
        }
    }

    private suspend fun refreshAllNow() {
        runCatching { api.getInstances() to api.getOnlineConversations() }
            .onSuccess { (response, online) ->
                val scopeChanged = settingsDataStore.reconcileVsCodeBinding(response.boundInstanceId)
                if (scopeChanged) conversationRepository.clearLocalCache()
                _uiState.update { state ->
                    state.copy(
                        loading = false,
                        instances = response.data,
                        boundInstanceId = response.boundInstanceId,
                        onlineConversations = online.data,
                        closingConversationKeys = state.closingConversationKeys.filterTo(mutableSetOf()) { key ->
                            online.data.any { "${it.instanceId}:${it.threadId}" == key }
                        },
                        scopeRevision = state.scopeRevision + if (scopeChanged) 1 else 0,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
    }

    fun refreshOnline() {
        viewModelScope.launch {
            refreshOnlineNow()
        }
    }

    private suspend fun refreshOnlineNow() {
        runCatching { api.getOnlineConversations() }
                .onSuccess { response ->
                    val scopeChanged = settingsDataStore.reconcileVsCodeBinding(response.boundInstanceId)
                    if (scopeChanged) conversationRepository.clearLocalCache()
                    _uiState.update { state ->
                        state.copy(
                            onlineConversations = response.data,
                            boundInstanceId = response.boundInstanceId,
                            closingConversationKeys = state.closingConversationKeys.filterTo(mutableSetOf()) { key ->
                                response.data.any { "${it.instanceId}:${it.threadId}" == key }
                            },
                            scopeRevision = state.scopeRevision + if (scopeChanged) 1 else 0,
                            error = null,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
    }

    private fun observeWindowChanges() {
        viewModelScope.launch {
            var sequence = 0L
            while (currentCoroutineContext().isActive) {
                try {
                    val response = bridgeEventsApi.poll(sequence)
                    sequence = if (response.resync) {
                        response.currentSequence
                    } else {
                        maxOf(sequence, response.currentSequence)
                    }
                    if (response.resync || response.data.any {
                            it.type == "vscode.instances.changed" || it.type == "vscode.binding.changed"
                        }
                    ) {
                        refreshAllNow()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    delay(1_500)
                }
            }
        }
    }

    fun bind(instanceId: String?, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { api.bind(instanceId) }
                .onSuccess {
                    val scopeChanged = settingsDataStore.reconcileVsCodeBinding(instanceId)
                    if (scopeChanged) conversationRepository.clearLocalCache()
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            boundInstanceId = instanceId,
                            scopeRevision = state.scopeRevision + if (scopeChanged) 1 else 0,
                        )
                    }
                    refresh()
                    onSuccess()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.message) }
                }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            runCatching { api.newChat() }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun openThread(threadId: String) {
        viewModelScope.launch {
            runCatching { api.openThread(threadId) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun openOnlineConversation(instanceId: String, threadId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                api.bind(instanceId)
                settingsDataStore.reconcileVsCodeBinding(instanceId).also { changed ->
                    if (changed) conversationRepository.clearLocalCache()
                }
                api.openThread(threadId)
            }.onSuccess {
                _uiState.update { it.copy(loading = false, boundInstanceId = instanceId) }
                refresh()
                onSuccess()
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun closeThread(instanceId: String, threadId: String) {
        val key = "$instanceId:$threadId"
        viewModelScope.launch {
            _uiState.update { it.copy(closingConversationKeys = it.closingConversationKeys + key, error = null) }
            runCatching { api.closeThread(instanceId, threadId) }
                .onSuccess {
                    repeat(12) {
                        delay(500)
                        val online = runCatching { api.getOnlineConversations() }.getOrNull() ?: return@repeat
                        _uiState.update { state -> state.copy(onlineConversations = online.data) }
                        if (online.data.none { it.instanceId == instanceId && it.threadId == threadId }) {
                            _uiState.update { state ->
                                state.copy(closingConversationKeys = state.closingConversationKeys - key)
                            }
                            return@launch
                        }
                    }
                    _uiState.update { state ->
                        state.copy(
                            closingConversationKeys = state.closingConversationKeys - key,
                            error = "退出对话超时，编辑器尚未确认关闭",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(closingConversationKeys = it.closingConversationKeys - key, error = error.message) }
                }
        }
    }
}
