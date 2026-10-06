package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.AppearanceSettingsScreen
import kotlinx.serialization.Serializable

/** Settings → General → Appearance: theme, interface style, accent colour. */
@Serializable data object AppearanceSettings : SettingsRoute

fun EntryProviderScope<NavKey>.appearanceSettingsEntry(onBack: () -> Unit) {
    entry<AppearanceSettings> {
        AppearanceSettingsScreen(onNavigateBack = onBack)
    }
}
