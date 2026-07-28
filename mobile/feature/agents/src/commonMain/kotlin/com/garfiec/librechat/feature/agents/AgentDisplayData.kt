package com.garfiec.librechat.feature.agents

import androidx.compose.runtime.Immutable

@Immutable
data class AgentCardDisplayData(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val author: String?,
    val authorName: String?,
)

@Immutable
data class AgentDetailDisplayData(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val author: String?,
    val authorName: String?,
    val model: String?,
    val category: String?,
    val tools: List<String>?,
    val conversationStarters: List<String>,
)

@Immutable
data class AgentHandoffDisplayData(
    val id: String,
    val name: String,
)

@Immutable
data class AgentActionDisplayData(
    val actionId: String?,
    val domain: String?,
    val type: String?,
    val authType: String?,
    val rawSpec: String?,
    val functionCount: Int,
)

@Immutable
data class AgentToolDisplayData(
    val toolId: String?,
    val name: String?,
    val description: String?,
    val icon: String?,
    val isAvailable: Boolean,
)
