package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreatePromptRequest(
    val prompt: CreatePromptData,
    val group: CreatePromptGroupData,
)

@Serializable
data class CreatePromptData(
    val prompt: String,
    val type: String,
)

@Serializable
data class CreatePromptGroupData(
    val name: String,
)
