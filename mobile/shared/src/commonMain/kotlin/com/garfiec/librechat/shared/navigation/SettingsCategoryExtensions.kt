package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.feature.settings.navigation.SettingsAccount
import com.garfiec.librechat.feature.settings.navigation.SettingsChat
import com.garfiec.librechat.feature.settings.navigation.SettingsData
import com.garfiec.librechat.feature.settings.navigation.SettingsGeneral
import com.garfiec.librechat.feature.settings.navigation.SettingsRoute

/** Maps a [SettingsCategory] to its corresponding typed navigation route. */
fun SettingsCategory.toRoute(): SettingsRoute = when (this) {
    SettingsCategory.GENERAL -> SettingsGeneral
    SettingsCategory.CHAT -> SettingsChat
    SettingsCategory.ACCOUNT -> SettingsAccount
    SettingsCategory.DATA -> SettingsData
}
