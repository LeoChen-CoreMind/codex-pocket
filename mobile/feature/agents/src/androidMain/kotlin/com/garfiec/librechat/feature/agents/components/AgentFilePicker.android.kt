package com.garfiec.librechat.feature.agents.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberAgentFilePicker(
    onFilePick: (fileRef: Any) -> Unit,
): AgentFilePicker {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            onFilePick(uri)
        }
    }
    return remember(launcher) {
        AgentFilePicker(launchAction = { mimeType -> launcher.launch(mimeType) })
    }
}

actual class AgentFilePicker(
    private val launchAction: (String) -> Unit,
) {
    actual fun launch(mimeType: String) {
        launchAction(mimeType)
    }
}
