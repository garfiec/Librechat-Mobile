package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.advanced_search
import com.garfiec.librechat.feature.settings.resources.advanced_search_desc
import com.garfiec.librechat.feature.settings.resources.section_search
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.UpdateCheckViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The General tab's content, without a Scaffold or top bar. */
@Composable
fun GeneralSettingsContent(
    onNavigateToAppearance: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GeneralSettingsViewModel = koinViewModel(),
    updateViewModel: UpdateCheckViewModel = koinViewModel(),
    appearanceViewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "appearance_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_appearance))
                }
                adaptiveSection {
                    row(key = "appearance_row") {
                        SettingsRow(
                            icon = Icons.Default.Palette,
                            title = stringResource(Res.string.appearance_theme_and_style),
                            summary = appearanceSummary(appearance.themeMode, appearance.uiStyle),
                            onClick = onNavigateToAppearance,
                        )
                    }
                }

                item(key = "tablet_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_layout))
                }
                adaptiveSection {
                    row(key = "tablet_sidebar_gesture") {
                        TabletSidebarGestureToggle(
                            gestureEnabled = uiState.tabletSidebarGestureEnabled,
                            onGestureEnabledChange = viewModel::setTabletSidebarGestureEnabled,
                        )
                    }
                }

                item(key = "general_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_language_region))
                }
                adaptiveSection {
                    row(key = "language_row") {
                        SettingsRow(
                            icon = Icons.Default.Language,
                            title = stringResource(Res.string.language),
                            summary = languageDisplayName(
                                uiState.selectedLanguage,
                                stringResource(Res.string.language_system_default),
                            ),
                            onClick = viewModel::showLanguageDialog,
                        )
                    }
                    row(key = "date_time_row") {
                        SettingsRow(
                            icon = Icons.Default.Schedule,
                            title = stringResource(Res.string.date_time_title),
                            summary = dateTimeSummary(),
                            onClick = viewModel::showDateTimeDialog,
                        )
                    }
                }

                item(key = "search_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_search))
                }
                adaptiveSection {
                    row(key = "advanced_search") {
                        SettingsSwitchRow(
                            icon = Icons.Default.ManageSearch,
                            title = stringResource(Res.string.advanced_search),
                            description = stringResource(Res.string.advanced_search_desc),
                            checked = uiState.advancedSearchEnabled,
                            onCheckedChange = viewModel::setAdvancedSearchEnabled,
                        )
                    }
                }

                if (updateViewModel.releaseNotesAvailable || updateViewModel.isSupported) {
                    item(key = "updates_header") {
                        AdaptiveSectionHeader(stringResource(Res.string.section_updates))
                    }
                }
                adaptiveSection {
                    if (updateViewModel.releaseNotesAvailable) {
                        row(key = "release_notes") {
                            SettingsRow(
                                icon = Icons.Default.Description,
                                title = stringResource(Res.string.release_notes_title),
                                summary = stringResource(Res.string.release_notes_desc, uiState.appVersion),
                                onClick = onNavigateToReleaseNotes,
                                // One divider closes the Updates group, under its last row.
                                showDivider = !updateViewModel.isSupported,
                            )
                        }
                    }
                    if (updateViewModel.isSupported) {
                        row(key = "update_check") {
                            UpdateCheckRow(
                                state = updateState.checkState,
                                onCheck = updateViewModel::check,
                                onOpenWhatsNew = onNavigateToWhatsNew,
                            )
                        }
                        row(key = "update_auto_check") {
                            UpdateAutoCheckToggle(
                                enabled = updateState.autoCheckEnabled,
                                onEnabledChange = updateViewModel::setAutoCheckEnabled,
                            )
                        }
                    }
                }

                item(key = "about_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_about))
                }
                adaptiveSection {
                    row(key = "about_info") {
                        AboutInfo(
                            serverUrl = uiState.serverUrl,
                            serverTrustedViaUserCa = uiState.serverTrustedViaUserCa,
                            appVersion = uiState.appVersion,
                            gitSha = uiState.gitSha,
                            serverVersion = uiState.serverVersion,
                            buildInfo = uiState.buildInfo,
                        )
                    }
                    row(key = "privacy_policy") {
                        val uriHandler = LocalUriHandler.current
                        SettingsRow(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(Res.string.privacy_policy_title),
                            summary = stringResource(Res.string.privacy_policy_desc),
                            // Android throws when no browser is installed (de-Googled ROMs, work profiles).
                            onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }

        // Language selector dialog
        if (uiState.showLanguageDialog) {
            LanguageSelectorDialog(
                selectedLanguage = uiState.selectedLanguage,
                onLanguageSelect = viewModel::setLanguage,
                onDismiss = viewModel::dismissLanguageDialog,
            )
        }

        if (uiState.showDateTimeDialog) {
            DateTimeSettingsDialog(
                prefs = uiState.dateTimePrefs,
                onSave = viewModel::saveDateTimePrefs,
                onDismiss = viewModel::dismissDateTimeDialog,
            )
        }
    } // Column
}

@Composable
private fun TabletSidebarGestureToggle(
    gestureEnabled: Boolean,
    onGestureEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSwitchRow(
        icon = Icons.Default.Tablet,
        title = stringResource(Res.string.sidebar_swipe_gesture),
        description = stringResource(Res.string.sidebar_swipe_gesture_desc),
        checked = gestureEnabled,
        onCheckedChange = onGestureEnabledChange,
        modifier = modifier,
    )
}

@Composable
internal fun AboutInfo(
    serverUrl: String,
    serverTrustedViaUserCa: Boolean,
    appVersion: String,
    gitSha: String,
    serverVersion: String?,
    buildInfo: BuildInfo?,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AboutRow(stringResource(Res.string.app_version_label), appVersion)
            if (gitSha.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AboutRow(stringResource(Res.string.app_build_label), gitSha)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.server_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = serverUrl.ifBlank { stringResource(Res.string.server_not_configured) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (serverTrustedViaUserCa) {
                Text(
                    text = stringResource(Res.string.server_user_ca_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            // Resolved LibreChat server version. Always shown — "Unknown" when it can't be
            // determined (LibreChat has no version endpoint; we infer it from the build commit).
            Spacer(modifier = Modifier.height(8.dp))
            AboutRow(
                label = stringResource(Res.string.server_version_label),
                value = serverVersion ?: stringResource(Res.string.server_version_unknown),
            )
            // Server build metadata (v0.8.6) — only when the server reports it.
            val commit = buildInfo?.commitShort ?: buildInfo?.commit
            if (!commit.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AboutRow(stringResource(Res.string.server_build_commit_label), commit)
            }
            buildInfo?.branch?.takeIf { it.isNotBlank() }?.let { branch ->
                Spacer(modifier = Modifier.height(8.dp))
                AboutRow(stringResource(Res.string.server_build_branch_label), branch)
            }
            buildInfo?.buildDate?.takeIf { it.isNotBlank() }?.let { buildDate ->
                Spacer(modifier = Modifier.height(8.dp))
                AboutRow(stringResource(Res.string.server_build_date_label), buildDate)
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * A label with its value on the trailing edge, or on the line below when a translation leaves them
 * too little room side by side: a version or a commit is no use cut short.
 */
@Composable
private fun AboutRow(label: String, value: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            // Counted when the pair is fitted on one line, so the two never run together.
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The published policy (docs/PRIVACY.md). Google Play and the App Store both require an in-app link;
 * it is shown on every channel. Installed builds keep this URL for good, so moving the file needs a
 * stub left at this path, plus the Play Console entry and PRIVACY.md section 16.
 */
private const val PRIVACY_POLICY_URL = "https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md"
