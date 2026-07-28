package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class BalanceConfig(
    val enabled: Boolean = false,
    val startBalance: Long? = null,
    val autoRefillEnabled: Boolean = false,
    val refillIntervalValue: Int? = null,
    val refillIntervalUnit: String? = null,
    val refillAmount: Long? = null,
)
