package com.garfiec.librechat.core.model

data class ServerConnection(
    val url: String,
    val name: String,
    val isDefault: Boolean = false,
)
