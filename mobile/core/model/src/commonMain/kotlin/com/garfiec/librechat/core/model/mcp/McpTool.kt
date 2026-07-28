package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    @SerialName("input_schema") val inputSchema: JsonObject? = null,
    @SerialName("server_name") val serverName: String? = null,
)
