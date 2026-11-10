package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.category_account
import com.garfiec.librechat.feature.settings.resources.category_administration
import com.garfiec.librechat.feature.settings.resources.category_chats
import com.garfiec.librechat.feature.settings.resources.category_privacy_data
import com.garfiec.librechat.feature.settings.resources.section_about
import com.garfiec.librechat.feature.settings.resources.section_language_region
import com.garfiec.librechat.feature.settings.screen.AboutSettingsPage
import com.garfiec.librechat.feature.settings.screen.AccountSettingsPage
import com.garfiec.librechat.feature.settings.screen.AdministrationSettingsPage
import com.garfiec.librechat.feature.settings.screen.ChatSettingsContent
import com.garfiec.librechat.feature.settings.screen.DataSettingsContent
import com.garfiec.librechat.feature.settings.screen.LanguageSettingsPage
import com.garfiec.librechat.feature.settings.screen.SettingsPageScaffold
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** A Settings category with a page of its own. Appearance keeps its own route, [AppearanceSettings]. */
@Serializable
enum class SettingsCategoryKey { ACCOUNT, CHATS, LANGUAGE, PRIVACY_DATA, ADMINISTRATION, ABOUT }

/** One Settings category's page. */
@Serializable data class SettingsCategory(val category: SettingsCategoryKey) : SettingsRoute

fun EntryProviderScope<NavKey>.settingsCategoryEntry(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToArchive: () -> Unit,
) {
    entry<SettingsCategory> { route ->
        SettingsCategoryScreen(
            category = route.category,
            onNavigate = onNavigate,
            onBack = onBack,
            onLogout = onLogout,
            onNavigateToArchive = onNavigateToArchive,
        )
    }
}

@Composable
private fun SettingsCategoryScreen(
    category: SettingsCategoryKey,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToArchive: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val title = when (category) {
        SettingsCategoryKey.ACCOUNT -> Res.string.category_account
        SettingsCategoryKey.CHATS -> Res.string.category_chats
        SettingsCategoryKey.LANGUAGE -> Res.string.section_language_region
        SettingsCategoryKey.PRIVACY_DATA -> Res.string.category_privacy_data
        SettingsCategoryKey.ADMINISTRATION -> Res.string.category_administration
        SettingsCategoryKey.ABOUT -> Res.string.section_about
    }
    SettingsPageScaffold(
        title = stringResource(title),
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        when (category) {
            SettingsCategoryKey.ACCOUNT -> AccountSettingsPage(
                onLogout = onLogout,
                onNavigateToApiKeys = { onNavigate(ApiKeys) },
                onNavigateToPasskeys = { onNavigate(Passkeys) },
                onNavigateToFavorites = { onNavigate(Favorites) },
                onNavigateToProviderKeys = { onNavigate(ProviderKeys()) },
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            // Today's Chat and Data tabs, until their own pages replace them.
            SettingsCategoryKey.CHATS -> ChatSettingsContent(
                onNavigateToPresets = { onNavigate(PresetManager) },
                onNavigateToContextUsage = { onNavigate(ContextUsageSettings) },
                onNavigateToChatLayout = { onNavigate(ChatLayoutSettings) },
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            SettingsCategoryKey.PRIVACY_DATA -> DataSettingsContent(
                onNavigateToArchive = onNavigateToArchive,
                onNavigateToSharedLinks = { onNavigate(SharedLinks) },
                onNavigateToArtifactShortcuts = { onNavigate(ArtifactShortcuts) },
                onNavigateToPrefetchSettings = { onNavigate(PrefetchSettings) },
                onNavigateToCodeExecution = { onNavigate(CodeExecution) },
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            SettingsCategoryKey.LANGUAGE -> LanguageSettingsPage(contentPadding = contentPadding)
            SettingsCategoryKey.ADMINISTRATION -> AdministrationSettingsPage(
                onNavigateToRoleSkillsAdmin = { onNavigate(RoleSkillsAdmin) },
                onNavigateToRoleMemoriesAdmin = { onNavigate(RoleMemoriesAdmin) },
                contentPadding = contentPadding,
            )
            SettingsCategoryKey.ABOUT -> AboutSettingsPage(
                onNavigateToWhatsNew = { onNavigate(WhatsNew) },
                onNavigateToReleaseNotes = { onNavigate(ReleaseNotes) },
                contentPadding = contentPadding,
            )
        }
    }
}
