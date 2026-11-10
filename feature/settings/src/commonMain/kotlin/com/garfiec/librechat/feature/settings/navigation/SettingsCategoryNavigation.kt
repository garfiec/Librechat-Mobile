package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.category_account
import com.garfiec.librechat.feature.settings.resources.category_administration
import com.garfiec.librechat.feature.settings.resources.category_advanced
import com.garfiec.librechat.feature.settings.resources.category_chats
import com.garfiec.librechat.feature.settings.resources.category_models_keys
import com.garfiec.librechat.feature.settings.resources.category_notifications
import com.garfiec.librechat.feature.settings.resources.category_privacy_data
import com.garfiec.librechat.feature.settings.resources.category_voice
import com.garfiec.librechat.feature.settings.resources.section_about
import com.garfiec.librechat.feature.settings.resources.section_appearance
import com.garfiec.librechat.feature.settings.resources.section_language_region
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

/** A Settings category with a page of its own. Appearance keeps its own route, [AppearanceSettings]. */
@Serializable
enum class SettingsCategoryKey { ACCOUNT, CHATS, NOTIFICATIONS, VOICE, MODELS, LANGUAGE, PRIVACY_DATA, ADMINISTRATION, ABOUT }

@Serializable data class SettingsCategory(val category: SettingsCategoryKey) : SettingsPaneRoute

/** Appearance, each category and Advanced, each in its own route's bar. */
fun EntryProviderScope<NavKey>.settingsPaneEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToArchive: () -> Unit,
) {
    entry<AppearanceSettings> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onNavigateToArchive) }
    entry<SettingsCategory> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onNavigateToArchive) }
    entry<AdvancedSettings> { SettingsPaneScreen(it, onNavigate, onBack, onLogout, onNavigateToArchive) }
}

/** The bar title of [this] page, on its route and its row on the Settings home. */
internal fun SettingsPaneRoute.titleRes(): StringResource = when (this) {
    AppearanceSettings -> Res.string.section_appearance
    is AdvancedSettings -> Res.string.category_advanced
    is SettingsCategory -> when (category) {
        SettingsCategoryKey.ACCOUNT -> Res.string.category_account
        SettingsCategoryKey.CHATS -> Res.string.category_chats
        SettingsCategoryKey.NOTIFICATIONS -> Res.string.category_notifications
        SettingsCategoryKey.VOICE -> Res.string.category_voice
        SettingsCategoryKey.MODELS -> Res.string.category_models_keys
        SettingsCategoryKey.LANGUAGE -> Res.string.section_language_region
        SettingsCategoryKey.PRIVACY_DATA -> Res.string.category_privacy_data
        SettingsCategoryKey.ADMINISTRATION -> Res.string.category_administration
        SettingsCategoryKey.ABOUT -> Res.string.section_about
    }
}

@Composable
private fun SettingsPaneScreen(
    route: SettingsPaneRoute,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
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
