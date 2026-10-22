package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.RoleMemoriesAdminScreen
import kotlinx.serialization.Serializable

@Serializable data object RoleMemoriesAdmin : SettingsRoute

fun EntryProviderScope<NavKey>.roleMemoriesAdminEntry(
    onBack: () -> Unit,
) {
    entry<RoleMemoriesAdmin> {
        RoleMemoriesAdminScreen(
            onNavigateBack = onBack,
        )
    }
}
