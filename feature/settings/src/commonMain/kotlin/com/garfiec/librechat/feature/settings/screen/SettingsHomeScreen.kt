package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveSectionScope
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.feature.settings.navigation.AdvancedSettings
import com.garfiec.librechat.feature.settings.navigation.AppearanceSettings
import com.garfiec.librechat.feature.settings.navigation.SettingsCategory
import com.garfiec.librechat.feature.settings.navigation.SettingsCategoryKey
import com.garfiec.librechat.feature.settings.navigation.SettingsPaneRoute
import com.garfiec.librechat.feature.settings.navigation.titleRes
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

/** The profile card's lines. [name] falls back to the email, then to "Account" while the profile loads. */
@Immutable
internal data class SettingsHomeProfile(
    val name: String,
    val email: String?,
    val avatarUrl: String?,
    val serverHost: String?,
)

/** What each category row says it is set to. */
@Immutable
internal data class SettingsHomeSummaries(
    val appearance: String,
    val chats: String,
    val notifications: String,
    val voice: String,
    val models: String,
    val language: String,
    val privacy: String,
    val about: String,
)

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

    SettingsPageScaffold(
        title = stringResource(Res.string.title_settings),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth < TWO_PANE_MIN_WIDTH) {
                SettingsHomeContent(
                    profile = profile,
                    summaries = summaries,
                    showAdministration = account.isAdmin,
                    onOpen = onOpenPage,
                    contentPadding = contentPadding,
                )
            } else {
                SettingsHomeTwoPane(
                    profile = profile,
                    summaries = summaries,
                    showAdministration = account.isAdmin,
                    openPage = openPage,
                    onOpen = { openPage = it },
                    contentPadding = contentPadding,
                ) { pagePadding ->
                    // A new page starts at its own top, and Advanced at the section it was opened for.
                    key(openPage) { pageContent(openPage, { openPage = it }, pagePadding, snackbarHostState) }
                }
            }
        }
    }
}

/** From this width the home keeps its list on the left and shows the open page beside it. */
internal val TWO_PANE_MIN_WIDTH = 600.dp

private val LIST_PANE_WIDTH = 320.dp

/** The narrowest the open page gets: a phone's width, the least every page is laid out for. */
private val MIN_PAGE_WIDTH = 320.dp

private val SettingsPaneSaver = Saver<SettingsPaneRoute, String>(
    save = { Json.encodeToString(SettingsPaneRoute.serializer(), it) },
    restore = { Json.decodeFromString(SettingsPaneRoute.serializer(), it) },
)

/** The wide home: the category list, with [openPage]'s row filled, and [page] beside it. */
@Composable
internal fun SettingsHomeTwoPane(
    profile: SettingsHomeProfile,
    summaries: SettingsHomeSummaries,
    showAdministration: Boolean,
    openPage: SettingsPaneRoute,
    onOpen: (SettingsPaneRoute) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    page: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val listWidth = (maxWidth - MIN_PAGE_WIDTH).coerceAtMost(LIST_PANE_WIDTH)
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsHomeContent(
                profile = profile,
                summaries = summaries,
                showAdministration = showAdministration,
                onOpen = onOpen,
                modifier = Modifier.width(listWidth),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(direction),
                    top = top,
                    bottom = bottom,
                ),
                openPage = openPage,
            )
            Box(modifier = Modifier.weight(1f)) {
                page(PaddingValues(end = contentPadding.calculateEndPadding(direction), top = top, bottom = bottom))
            }
        }
    }
}

/** The profile card and category rows. [openPage] is the page open beside them on the wide home. */
@Composable
internal fun SettingsHomeContent(
    profile: SettingsHomeProfile,
    summaries: SettingsHomeSummaries,
    showAdministration: Boolean,
    onOpen: (SettingsPaneRoute) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    openPage: SettingsPaneRoute? = null,
) {
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "profile_card") {
                ProfileCard(
                    profile = profile,
                    selected = openPage?.let { it == SettingsCategory(SettingsCategoryKey.ACCOUNT) },
                    onClick = { onOpen(SettingsCategory(SettingsCategoryKey.ACCOUNT)) },
                )
            }
        }
        item(key = "categories_gap") { Spacer(modifier = Modifier.height(24.dp)) }
        adaptiveSection { homeRows(CATEGORY_ROWS, summaries, openPage, onOpen) }
        item(key = "more_gap") { Spacer(modifier = Modifier.height(24.dp)) }
        adaptiveSection {
            homeRows(MORE_ROWS.filter { showAdministration || it.key != "administration" }, summaries, openPage, onOpen)
        }
        item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/** A category row on the home; [summary] reads what it is set to. */
private class HomeRow(
    val key: String,
    val page: SettingsPaneRoute,
    val icon: ImageVector,
    val tint: SettingsTileTint = SettingsTileTint.NEUTRAL,
    val summary: @Composable (SettingsHomeSummaries) -> String,
) {
    /** Advanced is open whichever section it was opened at. */
    fun isOpen(open: SettingsPaneRoute): Boolean = if (page is AdvancedSettings) open is AdvancedSettings else open == page
}

private val CATEGORY_ROWS = listOf(
    HomeRow("appearance", AppearanceSettings, Icons.Default.Palette, SettingsTileTint.PRIMARY) { it.appearance },
    HomeRow("chats", SettingsCategory(SettingsCategoryKey.CHATS), Icons.Default.Forum, SettingsTileTint.SECONDARY) { it.chats },
    HomeRow("notifications", SettingsCategory(SettingsCategoryKey.NOTIFICATIONS), Icons.Default.Notifications, SettingsTileTint.PRIMARY) {
        it.notifications
    },
    HomeRow("voice", SettingsCategory(SettingsCategoryKey.VOICE), Icons.Default.RecordVoiceOver, SettingsTileTint.TERTIARY) {
        it.voice
    },
    HomeRow("models", SettingsCategory(SettingsCategoryKey.MODELS), Icons.Default.Key, SettingsTileTint.PRIMARY) { it.models },
    HomeRow("language", SettingsCategory(SettingsCategoryKey.LANGUAGE), Icons.Default.Language, SettingsTileTint.SECONDARY) {
        it.language
    },
    HomeRow("privacy_data", SettingsCategory(SettingsCategoryKey.PRIVACY_DATA), Icons.Default.Shield, SettingsTileTint.TERTIARY) {
        it.privacy
    },
)

private val MORE_ROWS = listOf(
    HomeRow("administration", SettingsCategory(SettingsCategoryKey.ADMINISTRATION), Icons.Default.AdminPanelSettings) {
        stringResource(Res.string.settings_home_admin_summary)
    },
    HomeRow("advanced", AdvancedSettings(), Icons.Default.Tune) { stringResource(Res.string.settings_home_advanced_summary) },
    HomeRow("about", SettingsCategory(SettingsCategoryKey.ABOUT), Icons.Default.Info) { it.about },
)

private fun AdaptiveSectionScope.homeRows(
    rows: List<HomeRow>,
    summaries: SettingsHomeSummaries,
    openPage: SettingsPaneRoute?,
    onOpen: (SettingsPaneRoute) -> Unit,
) {
    for (homeRow in rows) {
        row(key = homeRow.key) {
            SettingsRow(
                title = stringResource(homeRow.page.titleRes()),
                summary = homeRow.summary(summaries),
                icon = homeRow.icon,
                tileTint = homeRow.tint,
                selected = openPage?.let(homeRow::isOpen),
                onClick = { onOpen(homeRow.page) },
            )
        }
    }
}

/** Opens Account; a non-null [selected] marks it as [SettingsRow] does. */
@Composable
private fun ProfileCard(profile: SettingsHomeProfile, selected: Boolean?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected == true) Modifier.background(adaptiveSelectedRowColor) else Modifier)
            .settingsRowClick(onClick, selected)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(
            imageUrl = profile.avatarUrl,
            size = 56.dp,
            fallbackText = profile.name.take(1).uppercase(),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.email?.let { email ->
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            profile.serverHost?.let { host ->
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected == null) SettingsChevron()
    }
}
