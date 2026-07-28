package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Provider {
    @SerialName("openAI")
    OPENAI,

    @SerialName("anthropic")
    ANTHROPIC,

    @SerialName("azureOpenAI")
    AZURE,

    @SerialName("google")
    GOOGLE,

    @SerialName("vertexai")
    VERTEXAI,

    @SerialName("bedrock")
    BEDROCK,

    @SerialName("mistralai")
    MISTRALAI,

    @SerialName("mistral")
    MISTRAL,

    @SerialName("deepseek")
    DEEPSEEK,

    @SerialName("moonshot")
    MOONSHOT,

    @SerialName("openrouter")
    OPENROUTER,

    @SerialName("xai")
    XAI,
}
