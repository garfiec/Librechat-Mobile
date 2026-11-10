package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.category_advanced
import com.garfiec.librechat.feature.settings.screen.AdvancedSettingsPage
import com.garfiec.librechat.feature.settings.screen.SettingsPageScaffold
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** Advanced's sections in page order, named after the categories they extend. */
@Serializable
enum class AdvancedSection { CHATS, ARTIFACTS, DISPLAY, VOICE, SEARCH, SERVER, DEVELOPER, CODE_ENVIRONMENTS, TROUBLESHOOTING }

/** Every power setting on one page; [section] opens it scrolled to that section. */
@Serializable data class AdvancedSettings(val section: AdvancedSection? = null) : SettingsPaneRoute

fun EntryProviderScope<NavKey>.advancedSettingsEntry(onNavigate: (NavKey) -> Unit, onBack: () -> Unit) {
    entry<AdvancedSettings> { route ->
        val snackbarHostState = remember { SnackbarHostState() }
        SettingsPageScaffold(
            title = stringResource(Res.string.category_advanced),
            onNavigateBack = onBack,
            snackbarHostState = snackbarHostState,
        ) { contentPadding ->
            AdvancedSettingsPane(route.section, onNavigate, contentPadding, snackbarHostState)
        }
    }
}

/** Advanced's page without a bar, for its route and the wide Settings home. */
@Composable
internal fun AdvancedSettingsPane(
    section: AdvancedSection?,
    onNavigate: (NavKey) -> Unit,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    AdvancedSettingsPage(
        section = section,
        onNavigateToPresets = { onNavigate(PresetManager) },
        onNavigateToApiKeys = { onNavigate(ApiKeys) },
        onNavigateToCodeExecution = { onNavigate(CodeExecution) },
        contentPadding = contentPadding,
        snackbarHostState = snackbarHostState,
    )
}
