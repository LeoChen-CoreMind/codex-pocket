package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EphemeralAgent(
    val mcp: List<String>? = null,
    @SerialName("web_search") val webSearch: Boolean? = null,
    @SerialName("file_search") val fileSearch: Boolean? = null,
    @SerialName("execute_code") val executeCode: Boolean? = null,
    val artifacts: String? = null,
)
