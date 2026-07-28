package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationExport(
    val conversation: Conversation,
    val messages: List<Message>,
    val exportedAt: Long,
    val version: Int = 1,
)
