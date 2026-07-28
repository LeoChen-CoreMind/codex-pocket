package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateMemoryRequest(
    val key: String,
    val value: String,
)
