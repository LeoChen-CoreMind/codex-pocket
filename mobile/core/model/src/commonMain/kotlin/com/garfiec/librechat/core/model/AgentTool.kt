package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AgentTool(
    @SerialName("tool_id") val toolId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val pluginKey: String? = null,
    val icon: String? = null,
    val metadata: JsonElement? = null,
    val isAvailable: Boolean = true,
)
