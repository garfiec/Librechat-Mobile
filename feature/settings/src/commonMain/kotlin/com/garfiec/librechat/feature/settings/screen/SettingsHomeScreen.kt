package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.navigation.SettingsCategoryKey
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.displayHostOf
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
    val language: String,
    val privacy: String,
    val about: String,
)

/**
 * Settings' first page: the profile card, then one row per category with what it is set to. Nothing
 * here changes a setting.
 */
@Composable
fun SettingsHomeScreen(
    onNavigateBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCategory: (SettingsCategoryKey) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    appearanceViewModel: AppearanceSettingsViewModel = koinViewModel(),
    prefetchViewModel: PrefetchActivityViewModel = koinViewModel(),
) {
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()
    val general by generalViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val prefetch by prefetchViewModel.uiState.collectAsStateWithLifecycle()

    // Back from Account (or the web, via another app) the card shows a changed name or avatar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, accountViewModel) {
        var replaying = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !replaying) accountViewModel.refreshOnReturn()
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
    val summaries = SettingsHomeSummaries(
        appearance = appearanceSummary(appearance.themeMode, appearance.uiStyle),
        language = "$language · ${dateTimeSample()}",
        privacy = stringResource(Res.string.settings_home_offline_reading, prefetch.status.label()),
        about = if (serverVersion != null) {
            stringResource(Res.string.settings_home_about_summary, general.appVersion, serverVersion)
        } else {
            general.appVersion
        },
    )

    SettingsPageScaffold(
        title = stringResource(Res.string.title_settings),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        SettingsHomeContent(
            profile = profile,
            summaries = summaries,
            showAdministration = account.isAdmin,
            onOpenAppearance = onOpenAppearance,
            onOpenCategory = onOpenCategory,
            contentPadding = contentPadding,
        )
    }
}

@Composable
internal fun SettingsHomeContent(
    profile: SettingsHomeProfile,
    summaries: SettingsHomeSummaries,
    showAdministration: Boolean,
    onOpenAppearance: () -> Unit,
    onOpenCategory: (SettingsCategoryKey) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
            adaptiveSection {
                row(key = "profile_card") {
                    ProfileCard(profile = profile, onClick = { onOpenCategory(SettingsCategoryKey.ACCOUNT) })
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
                        onClick = onOpenAppearance,
                    )
                }
                row(key = "chats") {
                    SettingsRow(
                        title = stringResource(Res.string.category_chats),
                        summary = stringResource(Res.string.settings_home_chats_summary),
                        icon = Icons.Default.Forum,
                        tileTint = SettingsTileTint.SECONDARY,
                        onClick = { onOpenCategory(SettingsCategoryKey.CHATS) },
                    )
                }
                row(key = "language") {
                    SettingsRow(
                        title = stringResource(Res.string.section_language_region),
                        summary = summaries.language,
                        icon = Icons.Default.Language,
                        tileTint = SettingsTileTint.TERTIARY,
                        onClick = { onOpenCategory(SettingsCategoryKey.LANGUAGE) },
                    )
                }
                row(key = "privacy_data") {
                    SettingsRow(
                        title = stringResource(Res.string.category_privacy_data),
                        summary = summaries.privacy,
                        icon = Icons.Default.Shield,
                        tileTint = SettingsTileTint.PRIMARY,
                        onClick = { onOpenCategory(SettingsCategoryKey.PRIVACY_DATA) },
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
                            onClick = { onOpenCategory(SettingsCategoryKey.ADMINISTRATION) },
                        )
                    }
                }
                row(key = "about") {
                    SettingsRow(
                        title = stringResource(Res.string.section_about),
                        summary = summaries.about,
                        icon = Icons.Default.Info,
                        onClick = { onOpenCategory(SettingsCategoryKey.ABOUT) },
                    )
                }
            }
            item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ProfileCard(profile: SettingsHomeProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
