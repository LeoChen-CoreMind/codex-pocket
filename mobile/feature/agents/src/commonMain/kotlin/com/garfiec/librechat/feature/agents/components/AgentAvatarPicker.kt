package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AgentAvatarPicker(
    avatarUrl: String?,
    agentName: String,
    onImageSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
)
