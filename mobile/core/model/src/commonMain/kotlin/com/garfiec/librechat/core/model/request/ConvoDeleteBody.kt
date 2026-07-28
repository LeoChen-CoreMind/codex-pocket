package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConvoDeleteBody(
    val arg: ConvoDeleteArg,
)

@Serializable
data class ConvoDeleteArg(
    val conversationId: String,
    val source: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    val endpoint: String? = null,
)
