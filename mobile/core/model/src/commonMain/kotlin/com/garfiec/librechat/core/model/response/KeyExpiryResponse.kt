package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * Wire response from `GET /api/keys?name=<endpoint>`.
 *
 * Backend returns one of: `null`, the literal string `"never"`, or an ISO-8601 timestamp
 * string from a raw Mongoose `Date` serialization. See `DISCOVERY.md` for the
 * authoritative wire-shape reference. Preserved as `String` to avoid lossy parsing;
 * the keys-list ViewModel may parse with `Instant.parse()` for display.
 */
@Serializable
data class KeyExpiryResponse(
    val expiresAt: String? = null,
)
