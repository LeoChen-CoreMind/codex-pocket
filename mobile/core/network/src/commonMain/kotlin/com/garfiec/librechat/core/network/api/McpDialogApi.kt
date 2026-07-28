package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path
import kotlinx.serialization.Serializable

@Serializable
data class McpDialogConfigDto(
    val enabled: Boolean = false,
    val port: Int = 47832,
    val running: Boolean = false,
    val addresses: List<String> = emptyList(),
    val url: String? = null,
    val prompt: String = "",
)

@Serializable
data class McpDialogImageDto(val url: String, val alt: String = "")

@Serializable
data class PendingMcpDialogDto(
    val requestId: String,
    val title: String,
    val markdown: String,
    val images: List<McpDialogImageDto> = emptyList(),
    val choices: List<String> = emptyList(),
    val allowText: Boolean = true,
    val createdAt: Long,
)

@Serializable
data class PendingMcpDialogsResponse(val data: List<PendingMcpDialogDto> = emptyList())

@Serializable
private data class UpdateMcpDialogConfigRequest(val enabled: Boolean, val port: Int)

@Serializable
private data class McpDialogResponseRequest(
    val action: String,
    val text: String = "",
    val selectedChoices: List<String> = emptyList(),
)

class McpDialogApi(private val client: HttpClient) {
    suspend fun config(): McpDialogConfigDto =
        client.get { url { path("api/mcp-dialog/config") } }.body()

    suspend fun configure(enabled: Boolean, port: Int): McpDialogConfigDto =
        client.put {
            url { path("api/mcp-dialog/config") }
            setBody(UpdateMcpDialogConfigRequest(enabled, port))
        }.body()

    suspend fun pending(): PendingMcpDialogsResponse =
        client.get { url { path("api/mcp-dialog/requests") } }.body()

    suspend fun respond(
        requestId: String,
        action: String,
        text: String = "",
        selectedChoices: List<String> = emptyList(),
    ) {
        client.post {
            url { path("api/mcp-dialog/requests/$requestId/respond") }
            setBody(McpDialogResponseRequest(action, text, selectedChoices))
        }
    }
}
