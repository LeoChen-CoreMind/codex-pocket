package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun McpServerStatusIndicator(
    isConnected: Boolean?,
    modifier: Modifier = Modifier,
) {
    val color = when (isConnected) {
        true -> Color(0xFF4CAF50) // Green
        false -> Color(0xFFF44336) // Red
        null -> Color(0xFF9E9E9E) // Gray
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}
