package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ConvoUpdateBody(
    val arg: ConvoUpdateArg,
)

@Serializable
data class ConvoUpdateArg(
    val conversationId: String,
    val title: String? = null,
)
