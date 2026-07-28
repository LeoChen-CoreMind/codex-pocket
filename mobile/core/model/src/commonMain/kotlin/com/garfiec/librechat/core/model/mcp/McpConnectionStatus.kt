package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.Serializable

/**
 * Response from GET /api/mcp/connection/status.
 * Backend returns: { success: true, connectionStatus: { "serverName": { connectionState, requiresOAuth, error? } } }
 */
@Serializable
data class McpConnectionStatusResponse(
    val success: Boolean = false,
    val connectionStatus: Map<String, McpServerStatus> = emptyMap(),
)

@Serializable
data class McpServerStatus(
    val connectionState: String = "disconnected",
    val requiresOAuth: Boolean = false,
    val error: String? = null,
) {
    val isConnected: Boolean get() = connectionState == "connected"
}
