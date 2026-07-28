package com.garfiec.librechat.core.model.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Typed user-provided-key error envelope sent by the backend over the SSE stream.
 *
 * Mirrors web's emission sites in upstream `packages/data-schemas/src/methods/key.ts`,
 * `packages/api/src/utils/key.ts`, and the per-endpoint `initialize.ts` files. See
 * `DISCOVERY.md` for the authoritative wire-shape reference.
 *
 * Note: [ExpiredUserKey.expiredAt] is a server-locale string (e.g., `"4/30/2026, 8:30:00 PM"` or
 * `"30.04.2026, 20:30:00"`), NOT ISO 8601. It is preserved verbatim and substituted into the
 * localized template — do NOT call `Instant.parse()` on it.
 */
sealed class UserKeyError {
    /**
     * Endpoint is parsed from the server's JSON payload `endpoint` field. The server omits
     * the field on some emission paths, so it is nullable across all variants. Snackbar
     * callers fall back to the list view (no auto-open) when null.
     */
    abstract val endpoint: String?

    data class NoUserKey(override val endpoint: String?) : UserKeyError()
    data class ExpiredUserKey(override val endpoint: String?, val expiredAt: String) : UserKeyError()
    data class InvalidUserKey(override val endpoint: String?) : UserKeyError()
}

private val parserJson = Json { ignoreUnknownKeys = true }

/**
 * Parses a raw stream-error message into a [UserKeyError]. Returns null when the message is
 * not a JSON object, has no `type` field, the `type` value is unknown, or — for
 * `expired_user_key` — required fields are missing.
 *
 * Adversarial substring inputs like `"foo no_user_key bar"` correctly return null because the
 * parse step fails. This is the entire reason the parser is JSON-based, not substring-based.
 */
fun parseUserKeyError(rawMessage: String): UserKeyError? {
    val element = runCatching { parserJson.parseToJsonElement(rawMessage) }.getOrNull() ?: return null
    val obj = element as? JsonObject ?: return null
    // Safe-cast to JsonPrimitive: the `.jsonPrimitive` extension throws on JsonObject /
    // JsonArray values, which would crash the SSE error-mapping coroutine.
    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return null
    val endpoint = (obj["endpoint"] as? JsonPrimitive)?.contentOrNull
    val expiredAt = (obj["expiredAt"] as? JsonPrimitive)?.contentOrNull
    return when (type) {
        "no_user_key" -> UserKeyError.NoUserKey(endpoint)
        "expired_user_key" -> UserKeyError.ExpiredUserKey(
            endpoint = endpoint,
            expiredAt = expiredAt ?: return null,
        )
        "invalid_user_key" -> UserKeyError.InvalidUserKey(endpoint)
        else -> null
    }
}
