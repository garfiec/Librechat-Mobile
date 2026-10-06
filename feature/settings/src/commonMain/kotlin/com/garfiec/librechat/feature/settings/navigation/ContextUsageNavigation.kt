package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.ContextUsageSettingsScreen
import kotlinx.serialization.Serializable

/** Settings → Chat → Context usage: gauge placement, breakdown detail, compact suggestion. */
@Serializable data object ContextUsageSettings : SettingsRoute

fun EntryProviderScope<NavKey>.contextUsageSettingsEntry(onBack: () -> Unit) {
    entry<ContextUsageSettings> {
        ContextUsageSettingsScreen(onNavigateBack = onBack)
    }
}
