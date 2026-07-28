package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.PromptGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PromptGroupListResponse(
    val promptGroups: List<PromptGroup> = emptyList(),
    val pageNumber: JsonPrimitive? = null,
    val pageSize: JsonPrimitive? = null,
    val pages: JsonPrimitive? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    val after: String? = null,
)
