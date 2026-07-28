package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.model.ActionMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateActionRequest(
    @SerialName("action_id") val actionId: String? = null,
    val metadata: ActionMetadata,
    val functions: List<FunctionTool>,
)

@Serializable
data class FunctionTool(
    val type: String = "function",
    val function: FunctionDefinition,
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val strict: Boolean? = null,
)
