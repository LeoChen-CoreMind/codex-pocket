package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SseContentEvent(
    val type: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val parentMessageId: String? = null,
    val responseMessageId: String? = null,
    val text: String? = null,
    val message: Message? = null,
    val conversation: Conversation? = null,
    val content: List<MessageContentPart>? = null,
    val final: Boolean? = null,
    val sync: Boolean? = null,
    val created: Boolean? = null,
    val error: String? = null,
    val stepType: String? = null,
    val stepData: JsonElement? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val input: String? = null,
    val output: String? = null,
    val attachments: List<Attachment>? = null,
    val fileId: String? = null,
    val filename: String? = null,
    val fileType: String? = null,
    val extra: JsonObject? = null,
)
