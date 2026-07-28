package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class TurnstileOptions(
    val language: String? = null,
    val size: String? = null,
)
