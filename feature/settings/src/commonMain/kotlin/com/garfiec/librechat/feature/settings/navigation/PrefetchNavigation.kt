package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.PrefetchActivityScreen
import com.garfiec.librechat.feature.settings.screen.PrefetchSettingsScreen
import kotlinx.serialization.Serializable

/** Settings → Data → Background prefetch: the prefetch switches, depth and status summary. */
@Serializable data object PrefetchSettings : SettingsRoute

/** Detail behind the status summary on the Background prefetch page. */
@Serializable data object PrefetchActivity : SettingsRoute

fun EntryProviderScope<NavKey>.prefetchEntries(onNavigate: (NavKey) -> Unit, onBack: () -> Unit) {
    entry<PrefetchSettings> {
        PrefetchSettingsScreen(
            onNavigateBack = onBack,
            onNavigateToPrefetchActivity = { onNavigate(PrefetchActivity) },
        )
    }
    entry<PrefetchActivity> {
        PrefetchActivityScreen(onNavigateBack = onBack)
    }
}
