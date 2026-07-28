package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.SharedLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedLinksResponse(
    @SerialName("links") val links: List<SharedLink> = emptyList(),
    val nextCursor: String? = null,
    val hasNextPage: Boolean? = null,
)
