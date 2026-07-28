package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val label: String? = null,
    val value: String? = null,
)
