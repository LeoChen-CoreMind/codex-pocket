package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class VerifyEmailRequest(
    val token: String,
    val email: String? = null,
)

@Serializable
data class ResendVerificationRequest(
    val email: String,
)
