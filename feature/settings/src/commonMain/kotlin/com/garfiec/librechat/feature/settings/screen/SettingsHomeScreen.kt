package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.navigation.AppearanceSettings
import com.garfiec.librechat.feature.settings.navigation.SettingsPaneRoute
import com.garfiec.librechat.feature.settings.navigation.toPage
import com.garfiec.librechat.feature.settings.navigation.toRoute
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.notifications_off
import com.garfiec.librechat.feature.settings.resources.notifications_on
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.NotificationSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import com.garfiec.librechat.feature.settings.viewmodel.displayHostOf
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings' first page: the profile card, then one row per category with what it is set to. Nothing
 * here changes a setting. Narrow, a row opens its page through [onOpenPage]; from [TWO_PANE_MIN_WIDTH]
 * the list stays on the left and [pageContent] draws the open page beside it, opening Advanced there
 * through its `onSelectPage`.
 */
@Composable
fun SettingsHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenPage: (SettingsPaneRoute) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    appearanceViewModel: AppearanceSettingsViewModel = koinViewModel(),
    prefetchViewModel: PrefetchActivityViewModel = koinViewModel(),
    notificationsViewModel: NotificationSettingsViewModel = koinViewModel(),
    pageContent: @Composable (
        page: SettingsPaneRoute,
        onSelectPage: (SettingsPaneRoute) -> Unit,
        contentPadding: PaddingValues,
        snackbarHostState: SnackbarHostState,
    ) -> Unit,
) {
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()
    val general by generalViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val notifications by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    // Only the status is read here, so the rest of the offline-reading state doesn't recompose the home.
    val prefetchState = prefetchViewModel.uiState.collectAsStateWithLifecycle()
    val prefetchStatus by remember { derivedStateOf { prefetchState.value.status } }

    // Back from Account or Provider keys (or the web, via another app) the card and the Models & keys
    // row show a changed name, avatar or key count.
    OnResume(onFirst = accountViewModel::loadProviderKeysSummary) {
        accountViewModel.refreshOnReturn()
        accountViewModel.refreshProviderKeysSummary()
    }

    val user = account.user
    val name = user?.name?.takeIf { it.isNotBlank() }
    val email = user?.email?.takeIf { it.isNotBlank() }
    val profile = SettingsHomeProfile(
        name = name ?: email ?: stringResource(Res.string.category_account),
        email = email.takeIf { name != null },
        avatarUrl = user?.avatar,
        serverHost = displayHostOf(general.serverUrl),
    )
    val language = languageDisplayName(general.selectedLanguage, stringResource(Res.string.language_system_default))
    val serverVersion = general.serverVersion
    val homeChats = general.homeChats
    val providerKeys = account.providerKeysSummary
    val summaries = SettingsHomeSummaries(
        appearance = appearanceSummary(appearance.themeMode, appearance.uiStyle),
        chats = stringResource(
            if (homeChats.thinkingOpenByDefault) Res.string.settings_home_thinking_open else Res.string.settings_home_thinking_closed,
        ) + " · " + stringResource(Res.string.settings_home_artifacts, artifactDisplayModeSummary(homeChats.artifactDisplayMode)),
        notifications = stringResource(if (notifications.on) Res.string.notifications_on else Res.string.notifications_off),
        voice = stringResource(
            if (homeChats.autoReadEnabled) Res.string.settings_home_read_aloud_on else Res.string.settings_home_read_aloud_off,
        ),
        models = if (providerKeys is ProviderKeysSummary.Count) {
            stringResource(Res.string.settings_home_provider_keys, providerKeysCountLabel(providerKeys))
        } else {
            stringResource(Res.string.settings_home_models_summary)
        },
        language = "$language · ${dateTimeSample()}",
        privacy = stringResource(Res.string.settings_home_offline_reading, prefetchStatus.label()),
        about = if (serverVersion != null) {
            stringResource(Res.string.settings_home_about_summary, general.appVersion, serverVersion)
        } else {
            general.appVersion
        },
    )

    var openPage by rememberSaveable(stateSaver = SettingsPaneSaver) {
        mutableStateOf<SettingsPaneRoute>(AppearanceSettings)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsHomeScreenContent(
        profile = profile,
        summaries = summaries,
        showAdministration = account.isAdmin,
        openPage = openPage.toPage(),
        onOpenPage = { onOpenPage(it.toRoute()) },
        onSelectPage = { openPage = it.toRoute() },
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { pagePadding ->
        // A new page starts at its own top, and Advanced at the section it was opened for.
        key(openPage) { pageContent(openPage, { openPage = it }, pagePadding, snackbarHostState) }
    }
}

private val SettingsPaneSaver = Saver<SettingsPaneRoute, String>(
    save = { Json.encodeToString(SettingsPaneRoute.serializer(), it) },
    restore = { Json.decodeFromString(SettingsPaneRoute.serializer(), it) },
)
