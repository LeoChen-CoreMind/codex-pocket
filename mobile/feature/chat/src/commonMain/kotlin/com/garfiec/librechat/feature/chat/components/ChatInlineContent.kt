package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalChatConversationId = compositionLocalOf<String?> { null }

val LocalChatInlineContent = staticCompositionLocalOf<@Composable () -> Unit> { {} }
