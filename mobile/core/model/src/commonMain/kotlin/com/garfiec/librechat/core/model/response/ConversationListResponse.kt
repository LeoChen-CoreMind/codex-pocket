package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Conversation
import kotlinx.serialization.Serializable

@Serializable
data class ConversationListResponse(
    val conversations: List<Conversation> = emptyList(),
    val nextCursor: String? = null,
)
