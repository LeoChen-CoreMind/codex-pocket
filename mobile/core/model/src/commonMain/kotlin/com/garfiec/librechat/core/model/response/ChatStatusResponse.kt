package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ChatStatusResponse(
    val active: Boolean = false,
    val conversationId: String? = null,
)
