package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiKey(
    val id: String? = null,
    val name: String,
    @SerialName("key") val keyValue: String? = null,
    val keyPrefix: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val lastUsedAt: String? = null,
)

@Serializable
data class ApiKeysResponse(
    val keys: List<ApiKey> = emptyList(),
)
