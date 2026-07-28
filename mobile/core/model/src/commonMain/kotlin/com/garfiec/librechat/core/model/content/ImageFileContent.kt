package com.garfiec.librechat.core.model.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageFileContent(
    @SerialName("file_id") val fileId: String? = null,
    val filepath: String? = null,
    val filename: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
