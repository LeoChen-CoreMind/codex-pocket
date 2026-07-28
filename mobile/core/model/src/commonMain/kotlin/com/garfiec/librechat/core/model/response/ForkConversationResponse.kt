package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.Serializable

@Serializable
data class ForkConversationResponse(
    val conversation: Conversation,
    val messages: List<Message> = emptyList(),
)
