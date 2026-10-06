package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.UpdateCheckViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_general)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        GeneralSettingsContent(
            onNavigateToAppearance = onNavigateToAppearance,
            onNavigateToWhatsNew = onNavigateToWhatsNew,
            onNavigateToReleaseNotes = onNavigateToReleaseNotes,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * Reusable General settings content (without Scaffold/TopAppBar).
 * Used by both the standalone screen and the tabbed settings screen.
 */
@Composable
fun GeneralSettingsContent(
    onNavigateToAppearance: () -> Unit,
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = koinViewModel(),
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
                        GeneralSettingsRow(
                            icon = Icons.Default.Palette,
                            title = stringResource(Res.string.appearance_theme_and_style),
                            subtitle = appearanceSummary(appearance.themeMode, appearance.uiStyle),
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
                        GeneralSettingsRow(
                            icon = Icons.Default.Language,
                            title = stringResource(Res.string.language),
                            subtitle = languageDisplayName(
                                uiState.selectedLanguage,
                                stringResource(Res.string.language_system_default),
                            ),
                            onClick = viewModel::showLanguageDialog,
                        )
                    }
                    row(key = "date_time_row") {
                        GeneralSettingsRow(
                            icon = Icons.Default.Schedule,
                            title = stringResource(Res.string.date_time_title),
                            subtitle = dateTimeSummary(),
                            onClick = viewModel::showDateTimeDialog,
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
                            GeneralSettingsRow(
                                icon = Icons.Default.Description,
                                title = stringResource(Res.string.release_notes_title),
                                subtitle = stringResource(Res.string.release_notes_desc, uiState.appVersion),
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
                            appVersion = uiState.appVersion,
                            gitSha = uiState.gitSha,
                            serverVersion = uiState.serverVersion,
                            buildInfo = uiState.buildInfo,
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
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Tablet,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.sidebar_swipe_gesture),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.sidebar_swipe_gesture_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                AdaptiveSwitch(
                    checked = gestureEnabled,
                    onCheckedChange = onGestureEnabledChange,
                )
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AboutInfo(
    serverUrl: String,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.app_version_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (gitSha.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.app_build_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = gitSha,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun GeneralSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            color = adaptiveRowColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) AdaptiveDivider()
    }
}
