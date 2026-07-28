package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Feedback
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Instant

private val json = Json { ignoreUnknownKeys = true }

private const val MAX_MESSAGE_TEXT_CHARS = 256 * 1024
private const val MAX_CONTENT_CHARS = 384 * 1024
private const val MAX_CONTENT_PART_CHARS = 96 * 1024
private const val MAX_CONTENT_PARTS = 128
private const val MAX_AUXILIARY_JSON_CHARS = 64 * 1024
private const val FINAL_TEXT_RESERVE_CHARS = 128 * 1024
private const val TRUNCATION_MARKER = "\n\n[... content truncated by Codex Pocket ...]\n\n"

private fun String.bounded(maxChars: Int): String {
    if (length <= maxChars) return this
    if (maxChars <= TRUNCATION_MARKER.length) return take(maxChars.coerceAtLeast(0))
    val available = (maxChars - TRUNCATION_MARKER.length).coerceAtLeast(0)
    val head = (available * 0.7).toInt()
    val tail = available - head
    return take(head) + TRUNCATION_MARKER + if (tail > 0) takeLast(tail) else ""
}

private fun List<MessageContentPart>.boundedContent(): List<MessageContentPart> {
    var remaining = MAX_CONTENT_CHARS
    val hasTextPart = any { !it.text.isNullOrEmpty() }
    var textPartConsumed = false

    fun availableBudget(preserveText: Boolean): Int {
        val reserve = if (preserveText && hasTextPart && !textPartConsumed) FINAL_TEXT_RESERVE_CHARS else 0
        return (remaining - reserve).coerceAtLeast(0)
    }

    fun consume(value: String?, preserveText: Boolean = false): String? {
        if (value == null || remaining <= 0) return null
        val available = availableBudget(preserveText)
        if (available <= 0) return null
        val result = value.bounded(min(MAX_CONTENT_PART_CHARS, available))
        remaining -= result.length
        return result
    }

    fun <T> consumeJson(value: T?, serialized: String?, preserveText: Boolean = true): T? {
        if (value == null || serialized == null || remaining <= 0) return null
        val chars = serialized.length
        if (chars > min(MAX_AUXILIARY_JSON_CHARS, availableBudget(preserveText))) return null
        remaining -= chars
        return value
    }

    return take(MAX_CONTENT_PARTS).map { part ->
        val text = consume(part.text)
        if (part.text != null) textPartConsumed = true
        val rawContent = consumeJson(part.content, part.content?.toString())
        val toolCall = part.toolCall?.let { call ->
            call.copy(
                output = consume(call.output, preserveText = true),
                subagentContent = consumeJson(call.subagentContent, call.subagentContent?.toString()),
            )
        }
        part.copy(
            text = text,
            think = consume(part.think, preserveText = true),
            error = consume(part.error, preserveText = true),
            toolCall = toolCall,
            content = rawContent,
        )
    }
}

fun Message.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId,
    conversationId = conversationId,
    parentMessageId = parentMessageId,
    sender = sender,
    text = text.bounded(MAX_MESSAGE_TEXT_CHARS),
    content = content?.boundedContent()?.let { json.encodeToString(ListSerializer(MessageContentPart.serializer()), it) },
    isCreatedByUser = isCreatedByUser,
    model = model,
    endpoint = endpoint,
    iconURL = iconURL,
    unfinished = unfinished,
    error = error,
    finishReason = finishReason,
    tokenCount = tokenCount,
    feedback = feedback?.let { json.encodeToString(Feedback.serializer(), it) },
    files = files?.let { json.encodeToString(ListSerializer(FileReference.serializer()), it) },
    attachments = attachments?.let { json.encodeToString(ListSerializer(Attachment.serializer()), it) },
    metadata = metadata?.toString()?.takeIf { it.length <= MAX_AUXILIARY_JSON_CHARS },
    quotes = quotes?.let { json.encodeToString(ListSerializer(String.serializer()), it) },
    createdAt = parseTimestamp(createdAt),
    updatedAt = parseTimestamp(updatedAt),
)

fun MessageEntity.toModel(): Message = Message(
    messageId = messageId,
    conversationId = conversationId,
    parentMessageId = parentMessageId,
    sender = sender,
    text = text ?: "",
    content = content?.let {
        try { json.decodeFromString<List<MessageContentPart>>(it) } catch (_: Exception) { null }
    },
    isCreatedByUser = isCreatedByUser,
    model = model,
    endpoint = endpoint,
    iconURL = iconURL,
    unfinished = unfinished,
    error = error,
    finishReason = finishReason,
    tokenCount = tokenCount,
    feedback = feedback?.let {
        try { json.decodeFromString<Feedback>(it) } catch (_: Exception) { null }
    },
    files = files?.let {
        try { json.decodeFromString<List<FileReference>>(it) } catch (_: Exception) { null }
    },
    attachments = attachments?.let {
        try { json.decodeFromString<List<Attachment>>(it) } catch (_: Exception) { null }
    },
    metadata = metadata?.let {
        try { json.decodeFromString<JsonObject>(it) } catch (_: Exception) { null }
    },
    quotes = quotes?.let {
        try { json.decodeFromString<List<String>>(it) } catch (_: Exception) { null }
    },
    createdAt = formatTimestamp(createdAt),
    updatedAt = formatTimestamp(updatedAt),
)

fun List<MessageEntity>.toModels(): List<Message> = map { it.toModel() }

private fun parseTimestamp(dateString: String?): Long {
    if (dateString == null) return Clock.System.now().toEpochMilliseconds()
    return try {
        Instant.parse(dateString).toEpochMilliseconds()
    } catch (_: Exception) {
        Clock.System.now().toEpochMilliseconds()
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    return Instant.fromEpochMilliseconds(epochMillis).toString()
}
