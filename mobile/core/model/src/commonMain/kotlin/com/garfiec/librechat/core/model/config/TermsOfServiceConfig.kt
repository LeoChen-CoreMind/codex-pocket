package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class TermsOfServiceConfig(
    val externalUrl: String? = null,
)
