package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class BranchMessageRequest(
    val conversationId: String,
    val messageId: String,
    val agentId: String? = null,
)
