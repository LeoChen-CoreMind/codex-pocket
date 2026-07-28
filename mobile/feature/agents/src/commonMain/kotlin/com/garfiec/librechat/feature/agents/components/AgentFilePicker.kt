package com.garfiec.librechat.feature.agents.components

import androidx.compose.runtime.Composable

/**
 * Per-capability file picker for agent file attachments. Duplicated platform
 * abstraction (rather than depending on `:feature:files`) to keep the
 * feature-modules-depend-on-:core-only architecture rule intact.
 */
@Composable
expect fun rememberAgentFilePicker(
    onFilePick: (fileRef: Any) -> Unit,
): AgentFilePicker

expect class AgentFilePicker {
    fun launch(mimeType: String)
}
