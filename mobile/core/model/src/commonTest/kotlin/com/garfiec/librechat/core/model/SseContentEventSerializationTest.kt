package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class SseContentEventSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun contentDeltaEventRoundTrip() {
        val original = SseContentEvent(
            type = "content_delta",
            messageId = "msg-001",
            conversationId = "conv-001",
            text = "Hello ",
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun finalEventWithNestedModelsRoundTrip() {
        val msg = Message(messageId = "msg-f", conversationId = "conv-f", text = "Done")
        val convo = Conversation(conversationId = "conv-f", title = "Final Chat")
        val original = SseContentEvent(
            type = "final",
            final = true,
            message = msg,
            conversation = convo,
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("msg-f", decoded.message?.messageId)
        assertEquals("Final Chat", decoded.conversation?.title)
    }

    @Test
    fun toolCallEventRoundTrip() {
        val original = SseContentEvent(
            type = "tool_call_start",
            toolCallId = "tc-001",
            toolName = "web_search",
            input = """{"query": "kotlin multiplatform"}""",
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun stepEventWithJsonElementRoundTrip() {
        val stepData = buildJsonObject {
            put("tool_calls", "active")
            put("index", 0)
        }
        val original = SseContentEvent(
            type = "step",
            stepType = "tool_calls",
            stepData = stepData,
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun errorEventRoundTrip() {
        val original = SseContentEvent(
            type = "error",
            error = "Rate limit exceeded",
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun eventWithAttachmentsRoundTrip() {
        val original = SseContentEvent(
            type = "attachment_created",
            fileId = "file-001",
            filename = "output.png",
            fileType = "image/png",
            attachments = listOf(
                Attachment(
                    fileId = "file-001",
                    filename = "output.png",
                    type = "image/png",
                    width = 800,
                    height = 600,
                ),
            ),
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun eventWithContentPartsRoundTrip() {
        val original = SseContentEvent(
            type = "content",
            content = listOf(
                MessageContentPart(type = ContentType.TEXT, text = "Some text"),
                MessageContentPart(
                    type = ContentType.TOOL_CALL,
                    toolCall = AgentToolCall(
                        type = ToolCallType.FUNCTION,
                        name = "search",
                        id = "tc-1",
                    ),
                ),
                MessageContentPart(type = ContentType.THINK, think = "Let me think..."),
            ),
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun eventWithExtraJsonFieldsRoundTrip() {
        val original = SseContentEvent(
            type = "sync",
            sync = true,
            extra = JsonObject(mapOf("customKey" to JsonPrimitive("customValue"))),
        )
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun emptyEventRoundTrip() {
        val original = SseContentEvent()
        val encoded = json.encodeToString(SseContentEvent.serializer(), original)
        val decoded = json.decodeFromString(SseContentEvent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun deserializesServerSsePayload() {
        val serverJson = """
            {
                "type": "content_delta",
                "conversationId": "c-1",
                "messageId": "m-1",
                "parentMessageId": "m-0",
                "responseMessageId": "m-1",
                "text": "Hi there!",
                "created": true,
                "futureField": "should-be-ignored"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(SseContentEvent.serializer(), serverJson)
        assertEquals("content_delta", decoded.type)
        assertEquals("c-1", decoded.conversationId)
        assertEquals("Hi there!", decoded.text)
        assertEquals(true, decoded.created)
    }
}
