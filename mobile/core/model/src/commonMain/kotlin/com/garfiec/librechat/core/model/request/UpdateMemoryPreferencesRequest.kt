package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateMemoryPreferencesRequest(
    val enabled: Boolean,
)
