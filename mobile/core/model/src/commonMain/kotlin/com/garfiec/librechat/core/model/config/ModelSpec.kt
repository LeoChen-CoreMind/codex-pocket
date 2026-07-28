package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.Preset
import kotlinx.serialization.Serializable

@Serializable
data class ModelSpec(
    val name: String,
    val label: String? = null,
    val preset: Preset? = null,
    val iconURL: String? = null,
    val description: String? = null,
)
