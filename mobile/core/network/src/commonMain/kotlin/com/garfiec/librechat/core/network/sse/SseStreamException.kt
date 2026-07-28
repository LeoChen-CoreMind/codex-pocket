package com.garfiec.librechat.core.network.sse

/** Cross-platform exception for SSE stream errors (replaces java.io.IOException). */
class SseStreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
