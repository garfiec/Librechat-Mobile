package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.feature.settings.navigation.AdvancedSettings
import com.garfiec.librechat.feature.settings.navigation.AppearanceSettings
import com.garfiec.librechat.feature.settings.navigation.SettingsCategory
import com.garfiec.librechat.feature.settings.navigation.SettingsCategoryKey
import com.garfiec.librechat.feature.settings.navigation.SettingsPaneRoute
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import com.garfiec.librechat.feature.settings.viewmodel.displayHostOf
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.pluralStringResource
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
    val prefetch by prefetchViewModel.uiState.collectAsStateWithLifecycle()

    // Back from Account or Provider keys (or the web, via another app) the card and the Models & keys
    // row show a changed name, avatar or key count.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, accountViewModel) {
        var replaying = true
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (replaying) {
                accountViewModel.loadProviderKeysSummary()
            } else {
                accountViewModel.refreshOnReturn()
                accountViewModel.refreshProviderKeysSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        replaying = false
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        ) + " · " + stringResource(Res.string.settings_home_artifacts, artifactDisplayModeLabel(homeChats.artifactDisplayMode)),
        voice = stringResource(
            if (homeChats.autoReadEnabled) Res.string.settings_home_read_aloud_on else Res.string.settings_home_read_aloud_off,
        ),
        models = if (providerKeys is ProviderKeysSummary.Count) {
            val count = pluralStringResource(
                Res.plurals.provider_keys_row_summary,
                providerKeys.totalCount,
                providerKeys.setCount,
                providerKeys.totalCount,
            )
            stringResource(Res.string.settings_home_provider_keys, count)
        } else {
            stringResource(Res.string.settings_home_models_summary)
        },
        language = "$language · ${dateTimeSample()}",
        privacy = stringResource(Res.string.settings_home_offline_reading, prefetch.status.label()),
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
    fun isOpen(page: SettingsPaneRoute): Boolean? = openPage?.let { it == page }
    fun onCategory(key: SettingsCategoryKey) = onOpen(SettingsCategory(key))
    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
            adaptiveSection {
                row(key = "profile_card") {
                    ProfileCard(
                        profile = profile,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.ACCOUNT)),
                        onClick = { onCategory(SettingsCategoryKey.ACCOUNT) },
                    )
                }
            }
            item(key = "categories_gap") { Spacer(modifier = Modifier.height(24.dp)) }
            adaptiveSection {
                row(key = "appearance") {
                    SettingsRow(
                        title = stringResource(Res.string.section_appearance),
                        summary = summaries.appearance,
                        icon = Icons.Default.Palette,
                        tileTint = SettingsTileTint.PRIMARY,
                        selected = isOpen(AppearanceSettings),
                        onClick = { onOpen(AppearanceSettings) },
                    )
                }
                row(key = "chats") {
                    SettingsRow(
                        title = stringResource(Res.string.category_chats),
                        summary = summaries.chats,
                        icon = Icons.Default.Forum,
                        tileTint = SettingsTileTint.SECONDARY,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.CHATS)),
                        onClick = { onCategory(SettingsCategoryKey.CHATS) },
                    )
                }
                row(key = "voice") {
                    SettingsRow(
                        title = stringResource(Res.string.category_voice),
                        summary = summaries.voice,
                        icon = Icons.Default.RecordVoiceOver,
                        tileTint = SettingsTileTint.TERTIARY,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.VOICE)),
                        onClick = { onCategory(SettingsCategoryKey.VOICE) },
                    )
                }
                row(key = "models") {
                    SettingsRow(
                        title = stringResource(Res.string.category_models_keys),
                        summary = summaries.models,
                        icon = Icons.Default.Key,
                        tileTint = SettingsTileTint.PRIMARY,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.MODELS)),
                        onClick = { onCategory(SettingsCategoryKey.MODELS) },
                    )
                }
                row(key = "language") {
                    SettingsRow(
                        title = stringResource(Res.string.section_language_region),
                        summary = summaries.language,
                        icon = Icons.Default.Language,
                        tileTint = SettingsTileTint.SECONDARY,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.LANGUAGE)),
                        onClick = { onCategory(SettingsCategoryKey.LANGUAGE) },
                    )
                }
                row(key = "privacy_data") {
                    SettingsRow(
                        title = stringResource(Res.string.category_privacy_data),
                        summary = summaries.privacy,
                        icon = Icons.Default.Shield,
                        tileTint = SettingsTileTint.TERTIARY,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.PRIVACY_DATA)),
                        onClick = { onCategory(SettingsCategoryKey.PRIVACY_DATA) },
                    )
                }
            }
            item(key = "more_gap") { Spacer(modifier = Modifier.height(24.dp)) }
            adaptiveSection {
                if (showAdministration) {
                    row(key = "administration") {
                        SettingsRow(
                            title = stringResource(Res.string.category_administration),
                            summary = stringResource(Res.string.settings_home_admin_summary),
                            icon = Icons.Default.AdminPanelSettings,
                            selected = isOpen(SettingsCategory(SettingsCategoryKey.ADMINISTRATION)),
                            onClick = { onCategory(SettingsCategoryKey.ADMINISTRATION) },
                        )
                    }
                }
                row(key = "advanced") {
                    SettingsRow(
                        title = stringResource(Res.string.category_advanced),
                        summary = stringResource(Res.string.settings_home_advanced_summary),
                        icon = Icons.Default.Tune,
                        selected = openPage?.let { it is AdvancedSettings },
                        onClick = { onOpen(AdvancedSettings()) },
                    )
                }
                row(key = "about") {
                    SettingsRow(
                        title = stringResource(Res.string.section_about),
                        summary = summaries.about,
                        icon = Icons.Default.Info,
                        selected = isOpen(SettingsCategory(SettingsCategoryKey.ABOUT)),
                        onClick = { onCategory(SettingsCategoryKey.ABOUT) },
                    )
                }
            }
            item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

/** Opens Account; a non-null [selected] marks it as [SettingsRow] does. */
@Composable
private fun ProfileCard(profile: SettingsHomeProfile, selected: Boolean?, onClick: () -> Unit) {
    val click = if (selected != null) {
        Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected == true) Modifier.background(adaptiveSelectedRowColor) else Modifier)
            .then(click)
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
        if (selected == null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
