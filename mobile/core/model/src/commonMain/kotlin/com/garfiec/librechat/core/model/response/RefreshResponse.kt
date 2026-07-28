package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RefreshResponse(
    val token: String,
    val user: User? = null,
)
