package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class McpServerDisplayData(
    val name: String,
    val title: String?,
    val description: String?,
    val isConnected: Boolean,
)
