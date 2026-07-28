package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class ModelSpecs(
    val list: List<ModelSpec> = emptyList(),
)
