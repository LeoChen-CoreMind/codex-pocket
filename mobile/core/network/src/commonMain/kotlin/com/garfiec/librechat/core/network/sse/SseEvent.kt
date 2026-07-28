package com.garfiec.librechat.core.network.sse

data class SseEvent(
    val event: String = "",
    val data: String = "",
)
