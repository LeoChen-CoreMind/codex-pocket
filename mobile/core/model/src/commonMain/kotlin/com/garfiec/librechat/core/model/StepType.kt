package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StepType {
    @SerialName("tool_calls")
    TOOL_CALLS,

    @SerialName("message_creation")
    MESSAGE_CREATION,
}
