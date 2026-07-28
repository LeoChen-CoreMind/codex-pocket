package com.garfiec.librechat.core.network.api.dto

import com.garfiec.librechat.core.model.response.LoginResponse

data class LoginResult(
    val response: LoginResponse,
    val refreshToken: String?,
)
