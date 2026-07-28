package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteFilesRequest(
    val files: List<DeleteFileEntry>,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("tool_resource") val toolResource: String? = null,
    @SerialName("assistant_id") val assistantId: String? = null,
)

@Serializable
data class DeleteFileEntry(
    @SerialName("file_id") val fileId: String,
    val filepath: String,
)
