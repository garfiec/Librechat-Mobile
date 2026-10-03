package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.WhatsNewScreen
import kotlinx.serialization.Serializable

/** Release notes for an available app update. Reached from Settings → Updates and the chat banner. */
@Serializable data object WhatsNew : SettingsRoute

fun EntryProviderScope<NavKey>.whatsNewEntry(onBack: () -> Unit) {
    entry<WhatsNew> {
        WhatsNewScreen(onNavigateBack = onBack)
    }
}
