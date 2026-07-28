package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedLink(
    @SerialName("_id") val id: String? = null,
    val conversationId: String,
    val title: String? = null,
    val user: String? = null,
    val shareId: String? = null,
    val isPublic: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
