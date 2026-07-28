package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EndpointConfig(
    val type: String? = null,
    val order: Int? = null,
    val iconURL: String? = null,
    val modelDisplayLabel: String? = null,
    val name: String? = null,
    val userProvide: Boolean? = null,
    val userProvideURL: Boolean? = null,
    val capabilities: List<String> = emptyList(),
    val disableBuilder: Boolean? = null,
    val azure: Boolean? = null,
)
