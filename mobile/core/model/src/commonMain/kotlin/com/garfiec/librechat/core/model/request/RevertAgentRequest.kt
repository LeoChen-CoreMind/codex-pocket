package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RevertAgentRequest(
    @SerialName("version_index") val versionIndex: Int,
)
