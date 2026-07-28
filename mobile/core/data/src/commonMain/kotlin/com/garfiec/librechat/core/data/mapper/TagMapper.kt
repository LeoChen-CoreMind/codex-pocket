package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.model.ConversationTag
import kotlin.time.Clock
import kotlin.time.Instant

fun ConversationTag.toEntity(): ConversationTagEntity = ConversationTagEntity(
    tag = tag ?: "",
    user = user ?: "",
    description = description,
    count = count,
    position = position,
    createdAt = parseTagTimestamp(createdAt),
    updatedAt = parseTagTimestamp(updatedAt),
)

fun ConversationTagEntity.toModel(): ConversationTag = ConversationTag(
    id = null,
    tag = tag,
    user = user,
    description = description,
    count = count,
    position = position,
    createdAt = formatTagTimestamp(createdAt),
    updatedAt = formatTagTimestamp(updatedAt),
)

fun List<ConversationTagEntity>.toModels(): List<ConversationTag> = map { it.toModel() }

private fun parseTagTimestamp(dateString: String?): Long {
    if (dateString == null) return Clock.System.now().toEpochMilliseconds()
    return try {
        Instant.parse(dateString).toEpochMilliseconds()
    } catch (_: Exception) {
        Clock.System.now().toEpochMilliseconds()
    }
}

private fun formatTagTimestamp(epochMillis: Long): String {
    return Instant.fromEpochMilliseconds(epochMillis).toString()
}
