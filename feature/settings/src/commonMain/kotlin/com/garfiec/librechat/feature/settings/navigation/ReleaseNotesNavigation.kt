package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.ReleaseNotesScreen
import kotlinx.serialization.Serializable

/** Release notes for the installed build and the releases before it. Reached from Settings → Updates. */
@Serializable data object ReleaseNotes : SettingsRoute

fun EntryProviderScope<NavKey>.releaseNotesEntry(onBack: () -> Unit) {
    entry<ReleaseNotes> {
        ReleaseNotesScreen(onNavigateBack = onBack)
    }
}
