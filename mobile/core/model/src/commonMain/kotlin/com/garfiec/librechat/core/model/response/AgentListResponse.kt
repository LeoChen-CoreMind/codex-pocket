package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Agent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AgentListResponse(
    @SerialName("object") val objectType: String? = null,
    val data: List<Agent> = emptyList(),
    @SerialName("first_id") val firstId: String? = null,
    @SerialName("last_id") val lastId: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val after: String? = null,
)
