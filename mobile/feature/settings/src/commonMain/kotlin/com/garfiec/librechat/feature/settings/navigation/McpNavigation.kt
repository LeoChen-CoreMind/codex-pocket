package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.McpServersScreen
import kotlinx.serialization.Serializable

@Serializable data object McpServers : SettingsRoute

fun EntryProviderScope<NavKey>.mcpServersEntry(
    onBack: () -> Unit,
) {
    entry<McpServers> {
        McpServersScreen(
            onNavigateBack = onBack,
        )
    }
}
