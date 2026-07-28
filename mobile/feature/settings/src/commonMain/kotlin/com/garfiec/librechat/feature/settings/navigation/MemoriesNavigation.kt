package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.MemoriesScreen
import kotlinx.serialization.Serializable

@Serializable data object Memories : SettingsRoute

fun EntryProviderScope<NavKey>.memoriesEntry(
    onBack: () -> Unit,
) {
    entry<Memories> {
        MemoriesScreen(
            onNavigateBack = onBack,
        )
    }
}
