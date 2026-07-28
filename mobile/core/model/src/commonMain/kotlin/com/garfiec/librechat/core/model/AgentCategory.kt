package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentCategory(
    val value: String,
    val label: String? = null,
    val count: Int = 0,
    val description: String? = null,
)
