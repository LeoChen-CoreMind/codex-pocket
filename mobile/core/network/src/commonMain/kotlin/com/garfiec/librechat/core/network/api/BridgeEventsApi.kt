package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BridgeEventDto(
    val sequence: Long,
    val type: String,
    val threadId: String? = null,
    val payload: JsonObject? = null,
)

@Serializable
data class BridgeEventsResponse(
    val data: List<BridgeEventDto> = emptyList(),
    val currentSequence: Long = 0,
    val resync: Boolean = false,
)

class BridgeEventsApi(private val client: HttpClient) {
    suspend fun poll(since: Long, waitMs: Long = 20_000): BridgeEventsResponse =
        client.get {
            url { path("api/events/poll") }
            parameter("since", since)
            parameter("wait", waitMs)
        }.body()
}
