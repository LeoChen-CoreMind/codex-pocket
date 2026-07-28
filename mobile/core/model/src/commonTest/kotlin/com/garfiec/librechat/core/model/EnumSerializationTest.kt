package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnumSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun fromNameResolvesAllBuiltIns() {
        assertEquals(EModelEndpoint.OPENAI, EModelEndpoint.fromName("openAI"))
        assertEquals(EModelEndpoint.GOOGLE, EModelEndpoint.fromName("google"))
        assertEquals(EModelEndpoint.AZURE_OPENAI, EModelEndpoint.fromName("azureOpenAI"))
        assertEquals(EModelEndpoint.ANTHROPIC, EModelEndpoint.fromName("anthropic"))
        assertEquals(EModelEndpoint.AGENTS, EModelEndpoint.fromName("agents"))
        assertNull(EModelEndpoint.fromName("OpenRouter"))
        assertNull(EModelEndpoint.fromName("OPENAI"))
        assertNull(EModelEndpoint.fromName(""))
    }

    @Test
    fun builtInNamesIsAllNineWireFormatStrings() {
        val expected = setOf(
            "azureOpenAI", "openAI", "google", "anthropic",
            "assistants", "azureAssistants", "agents", "custom", "bedrock",
        )
        assertEquals(expected, EModelEndpoint.BUILT_IN_NAMES)
    }

    @Test
    fun eModelEndpointRoundTrip() {
        for (value in EModelEndpoint.entries) {
            val encoded = json.encodeToString(EModelEndpoint.serializer(), value)
            val decoded = json.decodeFromString(EModelEndpoint.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun eModelEndpointSerialNames() {
        val pairs = mapOf(
            EModelEndpoint.OPENAI to "\"openAI\"",
            EModelEndpoint.AZURE_OPENAI to "\"azureOpenAI\"",
            EModelEndpoint.GOOGLE to "\"google\"",
            EModelEndpoint.ANTHROPIC to "\"anthropic\"",
            EModelEndpoint.ASSISTANTS to "\"assistants\"",
            EModelEndpoint.AZURE_ASSISTANTS to "\"azureAssistants\"",
            EModelEndpoint.AGENTS to "\"agents\"",
            EModelEndpoint.CUSTOM to "\"custom\"",
            EModelEndpoint.BEDROCK to "\"bedrock\"",
        )
        for ((value, expected) in pairs) {
            val encoded = json.encodeToString(EModelEndpoint.serializer(), value)
            assertEquals(expected, encoded, "Serialized name mismatch for $value")
        }
    }

    @Test
    fun contentTypeRoundTrip() {
        for (value in ContentType.entries) {
            val encoded = json.encodeToString(ContentType.serializer(), value)
            val decoded = json.decodeFromString(ContentType.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun feedbackRatingRoundTrip() {
        for (value in FeedbackRating.entries) {
            val encoded = json.encodeToString(FeedbackRating.serializer(), value)
            val decoded = json.decodeFromString(FeedbackRating.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun feedbackRatingSerialNames() {
        assertEquals(
            "\"thumbsUp\"",
            json.encodeToString(FeedbackRating.serializer(), FeedbackRating.THUMBS_UP),
        )
        assertEquals(
            "\"thumbsDown\"",
            json.encodeToString(FeedbackRating.serializer(), FeedbackRating.THUMBS_DOWN),
        )
    }

    @Test
    fun toolCallTypeRoundTrip() {
        for (value in ToolCallType.entries) {
            val encoded = json.encodeToString(ToolCallType.serializer(), value)
            val decoded = json.decodeFromString(ToolCallType.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun stepTypeRoundTrip() {
        for (value in StepType.entries) {
            val encoded = json.encodeToString(StepType.serializer(), value)
            val decoded = json.decodeFromString(StepType.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun providerRoundTrip() {
        for (value in Provider.entries) {
            val encoded = json.encodeToString(Provider.serializer(), value)
            val decoded = json.decodeFromString(Provider.serializer(), encoded)
            assertEquals(value, decoded)
        }
    }
}
