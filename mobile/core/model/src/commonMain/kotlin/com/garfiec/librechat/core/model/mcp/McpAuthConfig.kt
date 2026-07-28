package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Maps to backend apiKey object: { source, authorization_type, key?, custom_header? }. */
@Serializable
data class McpApiKeyConfig(
    val source: McpApiKeySource = McpApiKeySource.USER,
    @SerialName("authorization_type") val authorizationType: McpAuthorizationType = McpAuthorizationType.BEARER,
    val key: String? = null,
    @SerialName("custom_header") val customHeader: String? = null,
)

@Serializable
enum class McpApiKeySource {
    @SerialName("admin")
    ADMIN,

    @SerialName("user")
    USER,
}

@Serializable
enum class McpAuthorizationType {
    @SerialName("bearer")
    BEARER,

    @SerialName("basic")
    BASIC,

    @SerialName("custom")
    CUSTOM,
}

/** Maps to backend oauth object. Only the most common fields are exposed in the UI. */
@Serializable
data class McpOAuthConfig(
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("token_url") val tokenUrl: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scope: String? = null,
)
