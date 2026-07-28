package com.garfiec.librechat.core.model.content

import kotlinx.serialization.Serializable

@Serializable
data class VideoUrlContent(
    val url: String? = null,
)
