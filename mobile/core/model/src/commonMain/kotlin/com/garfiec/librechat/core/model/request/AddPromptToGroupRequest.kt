package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class AddPromptToGroupRequest(
    val prompt: String,
    val type: String,
)
