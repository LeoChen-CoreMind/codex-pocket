package com.garfiec.librechat.core.model.content

import kotlinx.serialization.Serializable

@Serializable
data class AgentUpdateContent(
    val index: Int? = null,
    val runId: String? = null,
    val agentId: String? = null,
)
