package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.DateTimeSettingsScreen
import kotlinx.serialization.Serializable

/** Settings › Language & region › Date & time: timestamp style, clock and date format. */
@Serializable data object DateTimeSettings : SettingsRoute

fun EntryProviderScope<NavKey>.dateTimeSettingsEntry(onBack: () -> Unit) {
    entry<DateTimeSettings> {
        DateTimeSettingsScreen(onNavigateBack = onBack)
    }
}
