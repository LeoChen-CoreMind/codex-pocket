package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class TurnstileConfig(
    val siteKey: String? = null,
    val options: TurnstileOptions? = null,
)
