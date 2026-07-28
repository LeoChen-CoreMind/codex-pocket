package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SupportContact(
    val name: String? = null,
    val email: String? = null,
)
