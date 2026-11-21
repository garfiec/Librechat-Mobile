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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveSectionScope
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.feature.settings.navigation.SettingsCategoryKey
import com.garfiec.librechat.feature.settings.navigation.SettingsPage
import com.garfiec.librechat.feature.settings.navigation.titleRes
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.title_settings
import org.jetbrains.compose.resources.stringResource

/** The profile card's lines. [name] falls back to the email, then to "Account" while the profile loads. */
@Immutable
data class SettingsHomeProfile(
    val name: String,
    val email: String?,
    val avatarUrl: String?,
    val serverHost: String?,
)

/** What each category row says it is set to. */
@Immutable
data class SettingsHomeSummaries(
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
 * The Settings home's page: narrow, the category list, whose rows open their page through [onOpenPage];
 * from [TWO_PANE_MIN_WIDTH] the list stays on the left with [openPage] drawn beside it by [page], and a
 * row switches it through [onSelectPage].
 */
@Composable
fun SettingsHomeScreenContent(
    profile: SettingsHomeProfile,
    summaries: SettingsHomeSummaries,
    showAdministration: Boolean,
    openPage: SettingsPage,
    onOpenPage: (SettingsPage) -> Unit,
    onSelectPage: (SettingsPage) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    page: @Composable (contentPadding: PaddingValues) -> Unit,
) {
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
                    showAdministration = showAdministration,
                    onOpen = onOpenPage,
                    contentPadding = contentPadding,
                )
            } else {
                SettingsHomeTwoPane(
                    profile = profile,
                    summaries = summaries,
                    showAdministration = showAdministration,
                    openPage = openPage,
                    onOpen = onSelectPage,
                    contentPadding = contentPadding,
                    page = page,
                )
            }
        }
    }
}

/** From this width the home keeps its list on the left and shows the open page beside it. */
internal val TWO_PANE_MIN_WIDTH = 600.dp

private val LIST_PANE_WIDTH = 320.dp

/** The narrowest the open page gets: a phone's width, the least every page is laid out for. */
private val MIN_PAGE_WIDTH = 320.dp

/** The wide home: the category list, with [openPage]'s row filled, and [page] beside it. */
@Composable
internal fun SettingsHomeTwoPane(
    profile: SettingsHomeProfile,
    summaries: SettingsHomeSummaries,
    showAdministration: Boolean,
    openPage: SettingsPage,
    onOpen: (SettingsPage) -> Unit,
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
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    openPage: SettingsPage? = null,
) {
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "profile_card") {
                ProfileCard(
                    profile = profile,
                    selected = openPage?.let { it == SettingsPage.Category(SettingsCategoryKey.ACCOUNT) },
                    onClick = { onOpen(SettingsPage.Category(SettingsCategoryKey.ACCOUNT)) },
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
    val page: SettingsPage,
    val icon: ImageVector,
    val tint: SettingsTileTint = SettingsTileTint.NEUTRAL,
    val summary: @Composable (SettingsHomeSummaries) -> String,
) {
    /** Advanced is open whichever section it was opened at. */
    fun isOpen(open: SettingsPage): Boolean = if (page is SettingsPage.Advanced) open is SettingsPage.Advanced else open == page
}

private val CATEGORY_ROWS = listOf(
    HomeRow("appearance", SettingsPage.Appearance, Icons.Default.Palette, SettingsTileTint.PRIMARY) { it.appearance },
    HomeRow("chats", SettingsPage.Category(SettingsCategoryKey.CHATS), Icons.Default.Forum, SettingsTileTint.SECONDARY) { it.chats },
    HomeRow(
        "notifications",
        SettingsPage.Category(SettingsCategoryKey.NOTIFICATIONS),
        Icons.Default.Notifications,
        SettingsTileTint.PRIMARY,
    ) {
        it.notifications
    },
    HomeRow("voice", SettingsPage.Category(SettingsCategoryKey.VOICE), Icons.Default.RecordVoiceOver, SettingsTileTint.TERTIARY) {
        it.voice
    },
    HomeRow("models", SettingsPage.Category(SettingsCategoryKey.MODELS), Icons.Default.Key, SettingsTileTint.PRIMARY) { it.models },
    HomeRow("language", SettingsPage.Category(SettingsCategoryKey.LANGUAGE), Icons.Default.Language, SettingsTileTint.SECONDARY) {
        it.language
    },
    HomeRow("privacy_data", SettingsPage.Category(SettingsCategoryKey.PRIVACY_DATA), Icons.Default.Shield, SettingsTileTint.TERTIARY) {
        it.privacy
    },
)

private val MORE_ROWS = listOf(
    HomeRow("administration", SettingsPage.Category(SettingsCategoryKey.ADMINISTRATION), Icons.Default.AdminPanelSettings) {
        stringResource(Res.string.settings_home_admin_summary)
    },
    HomeRow("advanced", SettingsPage.Advanced(), Icons.Default.Tune) { stringResource(Res.string.settings_home_advanced_summary) },
    HomeRow("about", SettingsPage.Category(SettingsCategoryKey.ABOUT), Icons.Default.Info) { it.about },
)

private fun AdaptiveSectionScope.homeRows(
    rows: List<HomeRow>,
    summaries: SettingsHomeSummaries,
    openPage: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
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
