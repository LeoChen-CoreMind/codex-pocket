package com.garfiec.librechat.core.model.content

import kotlinx.serialization.Serializable

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null,
    val output: String? = null,
)
