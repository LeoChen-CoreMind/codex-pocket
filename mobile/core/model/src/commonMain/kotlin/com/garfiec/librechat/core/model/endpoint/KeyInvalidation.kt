package com.garfiec.librechat.core.model.endpoint

/**
 * Signal emitted by `KeyRepository.keyInvalidations` whenever a stored
 * user-provided key changes. Observers (e.g. the chat-side
 * `EndpointKeyStatusDelegate`) react by re-fetching the affected key state.
 */
sealed class KeyInvalidation {
    /** A single key changed, identified by its provider-key name (e.g. `"openAI"`). */
    data class ByName(val name: String) : KeyInvalidation()

    /** All keys were cleared (e.g. via `deleteAllKeys`). */
    data object All : KeyInvalidation()
}
