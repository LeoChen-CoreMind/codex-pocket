package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class McpServerType {
    @SerialName("sse")
    SSE,

    @SerialName("streamable-http")
    STREAMABLE_HTTP,

    @SerialName("http")
    HTTP,

    @SerialName("stdio")
    STDIO,

    @SerialName("websocket")
    WEBSOCKET,
}
