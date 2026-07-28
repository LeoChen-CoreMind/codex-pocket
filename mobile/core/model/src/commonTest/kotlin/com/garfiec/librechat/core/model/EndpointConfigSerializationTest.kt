package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalEndpointConfigRoundTrip() {
        val original = EndpointConfig()
        val encoded = json.encodeToString(EndpointConfig.serializer(), original)
        val decoded = json.decodeFromString(EndpointConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun fullyPopulatedEndpointConfigRoundTrip() {
        val original = EndpointConfig(
            type = "openAI",
            order = 1,
            iconURL = "https://example.com/icon.svg",
            modelDisplayLabel = "GPT-4o",
            name = "OpenAI",
            userProvide = true,
            userProvideURL = false,
            capabilities = listOf("image_input", "tools", "reasoning"),
            disableBuilder = false,
        )
        val encoded = json.encodeToString(EndpointConfig.serializer(), original)
        val decoded = json.decodeFromString(EndpointConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun endpointConfigDeserializesFromServerJson() {
        val serverJson = """
            {
                "type": "anthropic",
                "order": 2,
                "modelDisplayLabel": "Claude",
                "capabilities": ["image_input", "tools"],
                "disableBuilder": true,
                "someNewField": 42
            }
        """.trimIndent()
        val decoded = json.decodeFromString(EndpointConfig.serializer(), serverJson)
        assertEquals("anthropic", decoded.type)
        assertEquals(2, decoded.order)
        assertEquals("Claude", decoded.modelDisplayLabel)
        assertEquals(listOf("image_input", "tools"), decoded.capabilities)
        assertEquals(true, decoded.disableBuilder)
    }
}
