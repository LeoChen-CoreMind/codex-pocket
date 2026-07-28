package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class PromptMentionDisplayData(
    val name: String,
    val command: String?,
    val oneliner: String?,
)
