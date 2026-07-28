package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.BranchMessageRequest
import com.garfiec.librechat.core.model.request.FeedbackRequest
import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.http.path
import kotlinx.serialization.Serializable

@Serializable
data class MessagePageResponse(
    val messages: List<Message> = emptyList(),
    val nextCursor: String? = null,
    val backwardsCursor: String? = null,
)

class MessagesApi constructor(
    private val client: HttpClient,
) {
    suspend fun getMessages(conversationId: String): List<Message> =
        client.get {
            url { path("api/messages/$conversationId") }
        }.body()

    suspend fun getMessagePage(
        conversationId: String,
        cursor: String? = null,
        limit: Int = 20,
    ): MessagePageResponse = client.get {
        url { path("api/messages/$conversationId/page") }
        parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }.body()

    suspend fun updateMessage(conversationId: String, messageId: String, request: UpdateMessageRequest): Message =
        client.put {
            url { path("api/messages/$conversationId/$messageId") }
            setBody(request)
        }.body()

    suspend fun branchMessage(request: BranchMessageRequest): Message =
        client.post {
            url { path("api/messages/branch") }
            setBody(request)
        }.body()

    suspend fun updateFeedback(conversationId: String, messageId: String, feedback: String?) {
        client.put {
            url { path("api/messages/$conversationId/$messageId/feedback") }
            setBody(FeedbackRequest(feedback = feedback))
        }
    }
}
