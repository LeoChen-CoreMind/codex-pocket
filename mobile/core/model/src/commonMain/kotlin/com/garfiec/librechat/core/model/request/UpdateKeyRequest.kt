package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateKeyRequest(
    val name: String,
    val value: String,
    val expiresAt: String? = null,
)
