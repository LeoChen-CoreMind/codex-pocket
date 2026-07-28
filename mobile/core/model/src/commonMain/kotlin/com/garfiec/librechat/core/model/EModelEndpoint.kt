package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EModelEndpoint {
    @SerialName("azureOpenAI")
    AZURE_OPENAI,

    @SerialName("openAI")
    OPENAI,

    @SerialName("google")
    GOOGLE,

    @SerialName("anthropic")
    ANTHROPIC,

    @SerialName("assistants")
    ASSISTANTS,

    @SerialName("azureAssistants")
    AZURE_ASSISTANTS,

    @SerialName("agents")
    AGENTS,

    @SerialName("custom")
    CUSTOM,

    @SerialName("bedrock")
    BEDROCK;

    /** Wire-format name for this enum value (matches `@SerialName`). */
    fun toSerialName(): String = when (this) {
        AZURE_OPENAI -> "azureOpenAI"
        OPENAI -> "openAI"
        GOOGLE -> "google"
        ANTHROPIC -> "anthropic"
        ASSISTANTS -> "assistants"
        AZURE_ASSISTANTS -> "azureAssistants"
        AGENTS -> "agents"
        CUSTOM -> "custom"
        BEDROCK -> "bedrock"
    }

    companion object {
        /** Wire-format names for all built-in endpoints. */
        val BUILT_IN_NAMES: Set<String> = entries.map { it.toSerialName() }.toSet()

        /** Resolve a wire-format name back to the enum, or null for custom-endpoint names. */
        fun fromName(name: String): EModelEndpoint? =
            entries.firstOrNull { it.toSerialName() == name }
    }
}
