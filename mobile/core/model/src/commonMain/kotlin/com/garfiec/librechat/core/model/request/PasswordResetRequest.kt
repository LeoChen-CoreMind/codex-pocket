package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequest(
    val email: String,
)

@Serializable
data class ResetPasswordRequest(
    val userId: String,
    val token: String,
    val password: String,
    @SerialName("confirm_password") val confirmPassword: String,
)
