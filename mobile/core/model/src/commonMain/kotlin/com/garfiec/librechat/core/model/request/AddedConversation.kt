package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddedConversation(
    val conversationId: String? = null,
    val parentMessageId: String? = null,
    val endpoint: String? = null,
    val endpointType: String? = null,
    val modelDisplayLabel: String? = null,
    val key: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val model: String? = null,
    val modelLabel: String? = null,
    val spec: String? = null,
)
