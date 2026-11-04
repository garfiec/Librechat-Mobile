package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.PasskeysScreen
import kotlinx.serialization.Serializable

/** Settings › Account › Passkeys (v0.8.9-rc1, management only). */
@Serializable data object Passkeys : SettingsRoute

fun EntryProviderScope<NavKey>.passkeysEntry(
    onBack: () -> Unit,
) {
    entry<Passkeys> {
        PasskeysScreen(
            onNavigateBack = onBack,
        )
    }
}
