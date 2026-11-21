package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.AboutSettingsPage
import com.garfiec.librechat.feature.settings.screen.AccountSettingsPage
import com.garfiec.librechat.feature.settings.screen.AdministrationSettingsPage
import com.garfiec.librechat.feature.settings.screen.AdvancedSettingsPage
import com.garfiec.librechat.feature.settings.screen.AppearanceSettingsPage
import com.garfiec.librechat.feature.settings.screen.ChatsSettingsPage
import com.garfiec.librechat.feature.settings.screen.LanguageSettingsPage
import com.garfiec.librechat.feature.settings.screen.ModelsSettingsPage
import com.garfiec.librechat.feature.settings.screen.NotificationSettingsPage
import com.garfiec.librechat.feature.settings.screen.PrivacySettingsPage
import com.garfiec.librechat.feature.settings.screen.SettingsPageScaffold
import com.garfiec.librechat.feature.settings.screen.VoiceSettingsPage
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Settings › Appearance: theme, interface style, colour, chat appearance and the tablet sidebar. */
@Serializable data object AppearanceSettings : SettingsPaneRoute

@Serializable data class SettingsCategory(val category: SettingsCategoryKey) : SettingsPaneRoute

/** Every power setting on one page; [section] opens it scrolled to that section. */
@Serializable data class AdvancedSettings(val section: AdvancedSection? = null) : SettingsPaneRoute

/** Appearance, each category and Advanced, each in its own route's bar. */
fun EntryProviderScope<NavKey>.settingsPaneEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSignInAgain: () -> Unit,
    onNavigateToArchive: () -> Unit,
) {
    entry<AppearanceSettings> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onSignInAgain, onNavigateToArchive) }
    entry<SettingsCategory> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onSignInAgain, onNavigateToArchive) }
    entry<AdvancedSettings> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onSignInAgain, onNavigateToArchive) }
}

/** The bar title of [this] page, on its route and its row on the Settings home. */
internal fun SettingsPaneRoute.titleRes(): StringResource = toPage().titleRes()

internal fun SettingsPaneRoute.toPage(): SettingsPage = when (this) {
    AppearanceSettings -> SettingsPage.Appearance
    is AdvancedSettings -> SettingsPage.Advanced(section)
    is SettingsCategory -> SettingsPage.Category(category)
}

internal fun SettingsPage.toRoute(): SettingsPaneRoute = when (this) {
    SettingsPage.Appearance -> AppearanceSettings
    is SettingsPage.Advanced -> AdvancedSettings(section)
    is SettingsPage.Category -> SettingsCategory(key)
}

@Composable
private fun SettingsPaneScreen(
    route: SettingsPaneRoute,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSignInAgain: () -> Unit,
    onNavigateToArchive: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsPageScaffold(
        title = stringResource(route.titleRes()),
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        SettingsPaneContent(
            page = route,
            onNavigate = onNavigate,
            onLogout = onLogout,
            onSignInAgain = onSignInAgain,
            onNavigateToArchive = onNavigateToArchive,
            onOpenAdvancedSettings = { onNavigate(AdvancedSettings(it)) },
            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
        )
    }
}

/**
 * [page] without a bar: in its own route's scaffold, or beside the list on the wide Settings home,
 * where [onOpenAdvancedSettings] opens Advanced in the same pane. Sub-pages push through [onNavigate].
 */
@Composable
internal fun SettingsPaneContent(
    page: SettingsPaneRoute,
    onNavigate: (NavKey) -> Unit,
    onLogout: () -> Unit,
    onSignInAgain: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    when (page) {
        AppearanceSettings -> AppearanceSettingsPage(
            onNavigateToChatLayout = { onNavigate(ChatLayoutSettings) },
            contentPadding = contentPadding,
        )
        is AdvancedSettings -> AdvancedSettingsPage(
            section = page.section,
            onNavigateToPresets = { onNavigate(PresetManager) },
            onNavigateToApiKeys = { onNavigate(ApiKeys) },
            onNavigateToCodeExecution = { onNavigate(CodeExecution) },
            contentPadding = contentPadding,
            snackbarHostState = snackbarHostState,
        )
        is SettingsCategory -> when (page.category) {
            SettingsCategoryKey.ACCOUNT -> AccountSettingsPage(
                onLogout = onLogout,
                onSignInAgain = onSignInAgain,
                onNavigateToPasskeys = { onNavigate(Passkeys) },
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            SettingsCategoryKey.CHATS -> ChatsSettingsPage(
                onNavigateToContextUsage = { onNavigate(ContextUsageSettings) },
                onNavigateToArtifactShortcuts = { onNavigate(ArtifactShortcuts) },
                onOpenAdvancedSettings = onOpenAdvancedSettings,
                contentPadding = contentPadding,
            )
            SettingsCategoryKey.NOTIFICATIONS -> NotificationSettingsPage(contentPadding = contentPadding)
            SettingsCategoryKey.VOICE -> VoiceSettingsPage(
                onOpenAdvancedSettings = onOpenAdvancedSettings,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            SettingsCategoryKey.MODELS -> ModelsSettingsPage(
                onNavigateToFavorites = { onNavigate(Favorites) },
                onNavigateToProviderKeys = { onNavigate(ProviderKeys()) },
                contentPadding = contentPadding,
            )
            SettingsCategoryKey.PRIVACY_DATA -> PrivacySettingsPage(
                onNavigateToArchive = onNavigateToArchive,
                onNavigateToSharedLinks = { onNavigate(SharedLinks) },
                onNavigateToPrefetchSettings = { onNavigate(PrefetchSettings) },
                onOpenAdvancedSettings = onOpenAdvancedSettings,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
            SettingsCategoryKey.LANGUAGE -> LanguageSettingsPage(
                onNavigateToDateTime = { onNavigate(DateTimeSettings) },
                contentPadding = contentPadding,
            )
            SettingsCategoryKey.ADMINISTRATION -> AdministrationSettingsPage(
                onNavigateToRoleSkillsAdmin = { onNavigate(RoleSkillsAdmin) },
                onNavigateToRoleMemoriesAdmin = { onNavigate(RoleMemoriesAdmin) },
                contentPadding = contentPadding,
            )
            SettingsCategoryKey.ABOUT -> AboutSettingsPage(
                onNavigateToWhatsNew = { onNavigate(WhatsNew) },
                onNavigateToReleaseNotes = { onNavigate(ReleaseNotes) },
                onNavigateToReportProblem = { onNavigate(ReportProblem) },
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
