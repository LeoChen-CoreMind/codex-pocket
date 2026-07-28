package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Balance(
    val tokenCredits: Long = 0,
)
