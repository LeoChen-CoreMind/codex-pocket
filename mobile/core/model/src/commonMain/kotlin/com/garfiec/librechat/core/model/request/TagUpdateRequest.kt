package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class TagUpdateRequest(
    val tags: List<String>,
)
