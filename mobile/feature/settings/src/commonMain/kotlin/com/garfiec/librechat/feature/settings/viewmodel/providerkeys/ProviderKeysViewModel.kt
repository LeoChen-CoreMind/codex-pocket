package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_failed
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeysUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Backs the Provider API Keys list screen. Filters `/api/config` endpoints to the
 * subset that supports user-provided keys, fetches per-endpoint expiry on init,
 * and orchestrates revoke / revoke-all.
 *
 * After save/delete the VM re-runs `configRepository.fetchEndpoints()` and
 * `configRepository.fetchModels()` so the chat model selector picks up admin-side
 * changes (added/removed endpoints + key-dependent model lists).
 */
class ProviderKeysViewModel(
    private val keyRepository: KeyRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderKeysUiState())
    val uiState: StateFlow<ProviderKeysUiState> = _uiState.asStateFlow()

    /**
     * Single cancel-and-restart guard for all entry-list refreshes. A fresh refresh always
     * wins over an in-flight stale one regardless of which caller initiated it.
     *
     * [pendingForceFor] / [pendingForceAll] accumulate the work the in-flight job was supposed
     * to do so a cancellation does NOT lose it. Example race: the `endpointConfigs` collector
     * fires (`forceFor=emptySet`) while [onChildKeyChanged] is mid-save with
     * `forceFor=setOf(endpointName)` in flight; without merging, the just-mutated row would
     * fall back to its cached pre-save value. The pending sets are cleared only on successful
     * completion (the `finally` block is also reached on cancellation, where we keep them).
     */
    private var refreshJob: Job? = null
    private var pendingForceFor: Set<String> = emptySet()
    private var pendingForceAll: Boolean = false

    init {
        viewModelScope.launch {
            ensureEndpointsLoaded()
            refresh(forceAll = true).join()
        }
        // Pick up admin-side endpoint changes while the screen is foregrounded — a newly
        // enabled user-provide endpoint should appear in the list without requiring the
        // user to leave + return. The first emission is the cached value already consumed
        // by the init-block refresh; drop(1) skips it so we only re-derive on real changes.
        viewModelScope.launch {
            configRepository.endpointConfigs.drop(1).collect {
                refresh()
            }
        }
    }

    private suspend fun ensureEndpointsLoaded() {
        if (configRepository.endpointConfigs.value.isEmpty()) {
            configRepository.fetchEndpoints()
        }
    }

    /**
     * Single fan-out helper used by all entry-list refresh paths.
     *
     * @param forceFor endpoint names whose [KeyState] should be re-fetched even if a cached
     *   value is available. Used by [onChildKeyChanged] to refresh the just-mutated row.
     * @param forceAll when true, re-fetch every entry's [KeyState] from the server,
     *   ignoring cached values. Used by initial load and revoke-all success.
     *
     * Net-new endpoints (present in `endpointConfigs` but not in the previous entry list)
     * are always fetched regardless of [forceFor] / [forceAll] — otherwise they'd sit on
     * `KeyState.Loading` forever. Existing rows fall through to the cached value when
     * neither flag selects them.
     */
    private fun refresh(forceFor: Set<String> = emptySet(), forceAll: Boolean = false): Job {
        refreshJob?.cancel()
        // Merge in the new caller's request. If a prior in-flight job was just cancelled, its
        // requested work is still in `pendingForceFor` / `pendingForceAll` and will be picked
        // up by this restart. Cleared at the end of the launched block on successful completion.
        pendingForceFor = pendingForceFor + forceFor
        pendingForceAll = pendingForceAll || forceAll
        val mergedForceFor = pendingForceFor
        val mergedForceAll = pendingForceAll
        val job = viewModelScope.launch {
            val configs = configRepository.endpointConfigs.value
            val previousByName = _uiState.value.entries.associateBy { it.endpointName }
            val filtered = configs
                .mapNotNull { (key, cfg) -> filteredEntry(key, cfg) }
                .sortedBy { it.endpointName.lowercase() }

            // Surface initial-load progress only when the previous list was empty; otherwise
            // we'd unnecessarily flip the spinner each time the configs collector fires.
            if (previousByName.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, entries = filtered, error = null) }
            }

            val toFetch = filtered.filter { entry ->
                mergedForceAll ||
                    entry.endpointName in mergedForceFor ||
                    !previousByName.containsKey(entry.endpointName)
            }
            val freshStates = coroutineScope {
                toFetch.map { entry ->
                    async { entry.endpointName to keyStateFor(entry) }
                }.awaitAll()
            }.toMap()

            val updated = filtered.map { entry ->
                val state = freshStates[entry.endpointName]
                    ?: previousByName[entry.endpointName]?.keyState
                    ?: KeyState.Loading
                entry.copy(keyState = state)
            }

            _uiState.update { it.copy(isLoading = false, entries = updated) }
            // Clear pending only when we reach the end without cancellation. A
            // CancellationException unwinds before this line, so the merged work survives
            // for the next caller's restart.
            pendingForceFor = emptySet()
            pendingForceAll = false
        }
        refreshJob = job
        return job
    }

    fun openDialog(endpointName: String) {
        // The dialog's `LaunchedEffect(endpointName)` re-fires `refreshKeyState()` on each
        // open so the reused per-endpoint VM always shows fresh state.
        _uiState.update { it.copy(pendingDialogEndpoint = endpointName) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(pendingDialogEndpoint = null) }
    }

    fun showRevokeAllConfirm() {
        _uiState.update { it.copy(showRevokeAllConfirm = true) }
    }

    fun dismissRevokeAllConfirm() {
        _uiState.update { it.copy(showRevokeAllConfirm = false) }
    }

    /**
     * After a child dialog mutates a key (save or revoke), refresh the just-mutated row
     * and pick up any admin-side endpoint changes.
     *
     * Concurrency: [refresh] cancels any in-flight job but accumulates the requested
     * `forceFor` / `forceAll` work into [pendingForceFor] / [pendingForceAll] so the
     * just-mutated row is guaranteed to be re-fetched even if the `endpointConfigs`
     * collector cancels this call mid-flight (e.g. when `fetchEndpoints()` produces a
     * differing config map).
     */
    fun onChildKeyChanged(endpointName: String) {
        viewModelScope.launch {
            configRepository.fetchEndpoints()
            configRepository.fetchModels()
            refresh(forceFor = setOf(endpointName))
        }
    }

    fun revokeAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRevokingAll = true, showRevokeAllConfirm = false) }
            val result = keyRepository.deleteAllKeys()
            _uiState.update { it.copy(isRevokingAll = false) }
            when (result) {
                is Result.Success -> {
                    configRepository.fetchEndpoints()
                    configRepository.fetchModels()
                    refresh(forceAll = true)
                }
                is Result.Error -> {
                    val fallback = getString(Res.string.provider_keys_revoke_all_failed)
                    _uiState.update {
                        it.copy(error = result.message ?: fallback)
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    fun emitTransientMessage(message: String) {
        _uiState.update { it.copy(transientMessage = message) }
    }

    private fun filteredEntry(name: String, cfg: EndpointConfig): ProviderKeyEntry? {
        val needsKey = cfg.userProvide == true || cfg.userProvideURL == true
        if (!needsKey) return null
        return ProviderKeyEntry(endpointName = name, config = cfg)
    }

    private suspend fun keyStateFor(entry: ProviderKeyEntry): KeyState {
        val keyName = resolveProviderKeyName(entry.endpointName, entry.config)
        // Fail-closed: any error on the underlying GET resolves to KeyState.Unset so the
        // list renders predictable "Not set" rather than a stale prior value.
        return keyRepository.fetchKeyState(keyName).getOrNull() ?: KeyState.Unset
    }
}
