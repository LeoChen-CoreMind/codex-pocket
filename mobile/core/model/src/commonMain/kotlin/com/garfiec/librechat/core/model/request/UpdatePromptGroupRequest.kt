package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePromptGroupRequest(
    val name: String? = null,
    val oneliner: String? = null,
    val command: String? = null,
)
