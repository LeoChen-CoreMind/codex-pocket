package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class PendingInteractionDto(
    val requestId: String,
    val method: String,
    val threadId: String? = null,
    val turnId: String? = null,
    val itemId: String? = null,
    val params: JsonObject? = null,
    val createdAt: Long,
)

@Serializable
data class PendingInteractionsResponse(val data: List<PendingInteractionDto> = emptyList())

@Serializable
enum class InteractionAction {
    @SerialName("accept")
    ACCEPT,

    @SerialName("acceptForSession")
    ACCEPT_FOR_SESSION,

    @SerialName("decline")
    DECLINE,
}

@Serializable
private data class InteractionActionRequest(val action: InteractionAction)

@Serializable
private data class InteractionAnswersRequest(val answers: Map<String, List<String>>)

class BridgeInteractionsApi(private val client: HttpClient) {
    suspend fun pending(): PendingInteractionsResponse =
        client.get { url { path("api/interactions") } }.body()

    suspend fun respond(requestId: String, action: InteractionAction) {
        client.post {
            url { path("api/interactions/$requestId/action") }
            setBody(InteractionActionRequest(action))
        }
    }

    suspend fun answer(requestId: String, answers: Map<String, List<String>>) {
        client.post {
            url { path("api/interactions/$requestId/answer") }
            setBody(InteractionAnswersRequest(answers))
        }
    }
}
