package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class PrivacyPolicyConfig(
    val externalUrl: String? = null,
    val openNewTab: Boolean? = null,
)
