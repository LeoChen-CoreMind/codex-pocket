package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.endpoint.KeyInvalidation
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import kotlinx.coroutines.flow.SharedFlow

interface KeyRepository {
    /**
     * Emits a [KeyInvalidation] whenever a stored key is mutated:
     * [KeyInvalidation.ByName] for [updateKey] / [deleteKey], or [KeyInvalidation.All]
     * for [deleteAllKeys]. Observers (e.g. the chat-side `EndpointKeyStatusDelegate`)
     * use this to refresh per-endpoint key state so a key set in Provider Keys is
     * reflected on the next chat-send without a stale-cache rejection.
     */
    val keyInvalidations: SharedFlow<KeyInvalidation>

    /**
     * Fetches the per-provider key expiry and maps it to the canonical [KeyState]
     * (Unset / Set / Expired) using `now = Clock.System.now()`. Single source of
     * truth for the chat-side and settings-side key-status fan-outs so callers
     * don't re-implement the wire-string mapping.
     *
     * Errors from the underlying GET propagate as [Result.Error]; callers decide
     * whether to fail open or closed. A null/empty wire string maps to
     * [KeyState.Unset] inside [Result.Success].
     */
    suspend fun fetchKeyState(name: String): Result<KeyState>
    suspend fun updateKey(request: UpdateKeyRequest): Result<Unit>
    suspend fun deleteKey(name: String): Result<Unit>
    suspend fun deleteAllKeys(): Result<Unit>
}
