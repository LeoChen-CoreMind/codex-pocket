package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class LdapConfig(
    val enabled: Boolean = false,
    val username: Boolean? = null,
)
