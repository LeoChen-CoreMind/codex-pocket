package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Banner(
    val bannerId: String? = null,
    val message: String? = null,
    val displayFrom: String? = null,
    val displayTo: String? = null,
    val type: String? = null,
    val isPublic: Boolean? = null,
    val persistable: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
