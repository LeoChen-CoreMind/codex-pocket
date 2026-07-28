package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class McpOAuthStatus(
    val status: String? = null,
    @SerialName("server_name") val serverName: String? = null,
    val error: String? = null,
)

@Serializable
data class McpOAuthTokens(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
)
