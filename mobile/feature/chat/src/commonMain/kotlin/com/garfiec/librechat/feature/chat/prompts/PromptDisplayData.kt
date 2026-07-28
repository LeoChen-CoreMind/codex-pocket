package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Immutable

/**
 * Display data for a prompt group in the library list view.
 */
@Immutable
data class PromptGroupDisplayData(
    val id: String,
    val name: String,
    val oneliner: String?,
    val category: String?,
    val authorName: String,
    val command: String?,
    val promptText: String?,
)

/**
 * Display data for the prompt group detail screen,
 * including command, production prompt text, and prompt metadata.
 */
@Immutable
data class PromptGroupDetailDisplayData(
    val id: String,
    val name: String,
    val oneliner: String?,
    val category: String?,
    val authorName: String,
    val command: String?,
    val productionId: String?,
    val productionPromptText: String?,
    val promptCount: Int,
)
