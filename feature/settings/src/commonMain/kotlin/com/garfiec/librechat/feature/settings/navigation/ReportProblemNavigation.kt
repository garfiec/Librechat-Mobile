package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.ReportProblemScreen
import kotlinx.serialization.Serializable

/** The Report a problem form. Reached from Settings → About → Troubleshooting. */
@Serializable data object ReportProblem : SettingsRoute

fun EntryProviderScope<NavKey>.reportProblemEntry(onBack: () -> Unit) {
    entry<ReportProblem> {
        ReportProblemScreen(onBack = onBack)
    }
}
