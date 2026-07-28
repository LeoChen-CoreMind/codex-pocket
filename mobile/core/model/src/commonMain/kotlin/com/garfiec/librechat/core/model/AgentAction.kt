package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentAction(
    @SerialName("action_id") val actionId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val type: String? = null,
    val metadata: ActionMetadata? = null,
    val version: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ActionMetadata(
    val domain: String? = null,
    val auth: ActionAuth? = null,
    @SerialName("raw_spec") val rawSpec: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("oauth_client_id") val oauthClientId: String? = null,
    @SerialName("oauth_client_secret") val oauthClientSecret: String? = null,
    @SerialName("privacy_policy_url") val privacyPolicyUrl: String? = null,
)

@Serializable
data class ActionAuth(
    val type: String? = null,
    @SerialName("authorization_type") val authorizationType: String? = null,
    @SerialName("custom_auth_header") val customAuthHeader: String? = null,
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("client_url") val clientUrl: String? = null,
    val scope: String? = null,
    @SerialName("token_exchange_method") val tokenExchangeMethod: String? = null,
)
