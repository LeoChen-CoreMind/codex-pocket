package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val username: String,
    val password: String,
    @SerialName("confirm_password") val confirmPassword: String,
)
