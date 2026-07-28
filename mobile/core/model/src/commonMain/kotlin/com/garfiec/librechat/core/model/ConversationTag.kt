package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationTag(
    @SerialName("_id") val id: String? = null,
    val tag: String? = null,
    val user: String? = null,
    val description: String? = null,
    val count: Int = 0,
    val position: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
