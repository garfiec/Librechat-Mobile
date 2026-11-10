package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.AppearanceSettingsScreen
import kotlinx.serialization.Serializable

/** Settings › Appearance: theme, interface style, colour, chat appearance and the tablet sidebar. */
@Serializable data object AppearanceSettings : SettingsRoute

fun EntryProviderScope<NavKey>.appearanceSettingsEntry(onNavigate: (NavKey) -> Unit, onBack: () -> Unit) {
    entry<AppearanceSettings> {
        AppearanceSettingsScreen(
            onNavigateBack = onBack,
            onNavigateToChatLayout = { onNavigate(ChatLayoutSettings) },
        )
    }
}
