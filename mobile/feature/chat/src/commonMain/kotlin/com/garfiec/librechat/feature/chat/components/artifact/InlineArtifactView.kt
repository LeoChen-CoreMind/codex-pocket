package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders an artifact inline in a chat message. Fills the available width and
 * scales height to match the rendered content. Tapping opens the fullscreen
 * [ArtifactPanel] via [onTap].
 *
 * Used when the user has enabled inline rendering for the artifact's type in
 * Settings > Chat > Artifacts.
 */
@Composable
expect fun InlineArtifactView(
    artifact: Artifact,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
)
