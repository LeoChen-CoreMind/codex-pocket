package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class DuplicateConversationRequest(
    val conversationId: String,
    val title: String? = null,
)
