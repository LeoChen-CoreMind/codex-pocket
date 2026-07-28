package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    @SerialName("_id") val id: String? = null,
    val groupId: String,
    val author: String,
    val prompt: String,
    val type: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ProductionPromptEmbed(
    val prompt: String? = null,
)

@Serializable
data class PromptGroup(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val numberOfGenerations: Int = 0,
    val oneliner: String? = null,
    val category: String? = null,
    val productionId: String? = null,
    val author: String,
    val authorName: String,
    val command: String? = null,
    val prompts: List<Prompt> = emptyList(),
    val productionPrompt: ProductionPromptEmbed? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
