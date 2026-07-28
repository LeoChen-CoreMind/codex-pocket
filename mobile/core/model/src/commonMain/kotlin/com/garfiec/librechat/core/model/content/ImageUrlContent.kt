package com.garfiec.librechat.core.model.content

import kotlinx.serialization.Serializable

@Serializable
data class ImageUrlContent(
    val url: String? = null,
    val detail: String? = null,
)
