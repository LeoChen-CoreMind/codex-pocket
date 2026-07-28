package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.endpoint.KeyInvalidation
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.fromWire
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.core.network.api.KeysApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Clock

class KeyRepositoryImpl(
    private val keysApi: KeysApi,
) : KeyRepository {

    // replay=0: subscribers receive only invalidations emitted after they attach. Fresh
    // ChatViewModels must not auto-trigger a fan-out recompute on subscription.
    // DROP_OLDEST coalesces invalidation bursts when a subscriber is briefly slow.
    private val _keyInvalidations = MutableSharedFlow<KeyInvalidation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val keyInvalidations: SharedFlow<KeyInvalidation> = _keyInvalidations.asSharedFlow()

    override suspend fun fetchKeyState(name: String): Result<KeyState> {
        val wire = safeApiCall { keysApi.getKeyExpiry(name).expiresAt }
        return when (wire) {
            is Result.Success -> {
                val parsed = KeyState.fromWire(wire.data, Clock.System.now())
                if (parsed.malformedSource != null) {
                    Logger.w(tag = "KeyRepository") {
                        "fetchKeyState: malformed wire string '${parsed.malformedSource}' for key '$name' -> Unset"
                    }
                }
                Result.Success(parsed.state)
            }
            is Result.Error -> wire
            is Result.Loading -> Result.Loading
        }
    }

    override suspend fun updateKey(request: UpdateKeyRequest): Result<Unit> {
        val result = safeApiCall { keysApi.updateKey(request) }
        if (result is Result.Success) emitInvalidation(KeyInvalidation.ByName(request.name))
        return result
    }

    override suspend fun deleteKey(name: String): Result<Unit> {
        val result = safeApiCall { keysApi.deleteKey(name) }
        if (result is Result.Success) emitInvalidation(KeyInvalidation.ByName(name))
        return result
    }

    override suspend fun deleteAllKeys(): Result<Unit> {
        val result = safeApiCall { keysApi.deleteAllKeys() }
        if (result is Result.Success) emitInvalidation(KeyInvalidation.All)
        return result
    }

    private fun emitInvalidation(invalidation: KeyInvalidation) {
        val emitted = _keyInvalidations.tryEmit(invalidation)
        if (!emitted) {
            Logger.w {
                "keyInvalidations tryEmit failed (no subscriber yet?) — invalidation: $invalidation"
            }
        }
    }
}
