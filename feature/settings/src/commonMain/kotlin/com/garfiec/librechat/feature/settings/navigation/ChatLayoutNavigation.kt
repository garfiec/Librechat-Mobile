package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.ChatLayoutSettingsScreen
import kotlinx.serialization.Serializable

/** Settings › Appearance › Chat appearance: layout, text size, title bar, bubbles, avatars. */
@Serializable data object ChatLayoutSettings : SettingsRoute

fun EntryProviderScope<NavKey>.chatLayoutSettingsEntry(onBack: () -> Unit) {
    entry<ChatLayoutSettings> {
        ChatLayoutSettingsScreen(onNavigateBack = onBack)
    }
}
