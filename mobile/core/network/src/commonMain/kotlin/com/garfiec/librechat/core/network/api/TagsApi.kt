package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.request.TagUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

class TagsApi(
    private val client: HttpClient,
) {
    suspend fun getTags(): List<ConversationTag> =
        client.get {
            url { path("api/tags") }
        }.body()

    suspend fun updateConversationTags(conversationId: String, tags: List<String>) {
        client.put {
            url { path("api/tags/convo/$conversationId") }
            setBody(TagUpdateRequest(tags = tags))
        }
    }
}
