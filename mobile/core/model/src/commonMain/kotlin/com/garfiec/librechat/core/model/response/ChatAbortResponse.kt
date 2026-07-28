package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ChatAbortResponse(
    val success: Boolean = true,
)
