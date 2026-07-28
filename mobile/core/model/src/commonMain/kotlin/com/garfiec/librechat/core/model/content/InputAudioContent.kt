package com.garfiec.librechat.core.model.content

import kotlinx.serialization.Serializable

@Serializable
data class InputAudioContent(
    val data: String? = null,
    val format: String? = null,
)
