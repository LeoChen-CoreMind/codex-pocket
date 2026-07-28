package com.garfiec.librechat.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TwoFactorConfirmRequest(
    val token: String,
)
