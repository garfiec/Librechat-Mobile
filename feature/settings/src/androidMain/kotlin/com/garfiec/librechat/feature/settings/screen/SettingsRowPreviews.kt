package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.prefetch.PrefetchConversationStatus
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.advanced_search
import com.garfiec.librechat.feature.settings.resources.advanced_search_desc
import com.garfiec.librechat.feature.settings.resources.client_image_resize
import com.garfiec.librechat.feature.settings.resources.client_image_resize_enforced
import com.garfiec.librechat.feature.settings.resources.delete_account
import com.garfiec.librechat.feature.settings.resources.language
import com.garfiec.librechat.feature.settings.resources.language_system_default
import com.garfiec.librechat.feature.settings.resources.prefetch_cache_images
import com.garfiec.librechat.feature.settings.resources.prefetch_cache_messages
import com.garfiec.librechat.feature.settings.resources.prefetch_cache_messages_value
import com.garfiec.librechat.feature.settings.resources.prefetch_summary_last_run
import com.garfiec.librechat.feature.settings.resources.prefetch_summary_scheduled_run
import com.garfiec.librechat.feature.settings.resources.prefetch_summary_status
import com.garfiec.librechat.feature.settings.resources.prefetch_summary_warmed
import com.garfiec.librechat.feature.settings.resources.prefetch_summary_warmed_value
import com.garfiec.librechat.feature.settings.resources.section_appearance
import com.garfiec.librechat.feature.settings.resources.section_danger_zone
import com.garfiec.librechat.feature.settings.state.PrefetchDisplayStatus
import com.garfiec.librechat.feature.settings.state.PrefetchPauseReason
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchCondition
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchConditionRow
import org.jetbrains.compose.resources.stringResource

// Label and value rows across Settings, at the widths and translations that squeeze them.

@PreviewNarrowScreens
@Composable
private fun SettingsRowsPreview() = PreviewSurface(UiStyle.MATERIAL) { SettingsRowsHost() }

@PreviewNarrowScreens
@Composable
private fun SettingsRowsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { SettingsRowsHost() }

@PreviewNarrowScreens
@Composable
private fun PrefetchActivityRowsPreview() = PreviewSurface(UiStyle.MATERIAL) { PrefetchActivityRowsHost() }

@PreviewNarrowScreens
@Composable
private fun PrefetchActivityRowsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { PrefetchActivityRowsHost() }

@PreviewNarrowScreens
@Composable
private fun PrefetchSettingsPreview() = PreviewSurface(UiStyle.MATERIAL) { PrefetchSettingsHost() }

@PreviewNarrowScreens
@Composable
private fun AboutInfoPreview() = PreviewSurface(UiStyle.MATERIAL) { AboutInfoHost() }

@PreviewNarrowScreens
@Composable
private fun DataButtonsPreview() = PreviewSurface(UiStyle.MATERIAL) { DataButtonsHost() }

@PreviewNarrowScreens
@Composable
private fun DataButtonsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { DataButtonsHost() }

@PreviewNarrowScreens
@Composable
private fun SpeechButtonsPreview() = PreviewSurface(UiStyle.MATERIAL) { SpeechDetailButtons({}, {}) }

@PreviewNarrowScreens
@Composable
private fun NudgePreviewPreview() = PreviewSurface(UiStyle.MATERIAL) { NudgeHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

/** Every row kind in grouped cells: icon tile and summary, plain, switch, disabled switch, destructive. */
@Composable
private fun SettingsRowsHost() {
    AdaptiveGroupedPage {
        LazyColumn {
            item { AdaptiveSectionHeader(stringResource(Res.string.section_appearance)) }
            adaptiveSection {
                row(key = "nav") {
                    SettingsRow(
                        title = stringResource(Res.string.section_appearance),
                        summary = "Dark · Material",
                        icon = Icons.Default.Palette,
                        tileTint = SettingsTileTint.PRIMARY,
                        onClick = {},
                    )
                }
                row(key = "plain") {
                    SettingsRow(
                        title = stringResource(Res.string.language),
                        summary = stringResource(Res.string.language_system_default),
                        onClick = {},
                    )
                }
                row(key = "switch") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.advanced_search),
                        description = stringResource(Res.string.advanced_search_desc),
                        checked = true,
                        onCheckedChange = {},
                        icon = Icons.Default.ManageSearch,
                    )
                }
                row(key = "disabled") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.client_image_resize),
                        description = stringResource(Res.string.client_image_resize_enforced),
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                    )
                }
            }
            item { AdaptiveSectionHeader(stringResource(Res.string.section_danger_zone)) }
            adaptiveSection {
                row(key = "destructive") {
                    SettingsRow(
                        title = stringResource(Res.string.delete_account),
                        icon = Icons.Default.Delete,
                        onClick = {},
                        destructive = true,
                    )
                }
            }
        }
    }
}

/** The activity page's rows in its own order: the summary, the gate, the queue and the cache. */
@Composable
private fun PrefetchActivityRowsHost() {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailRow(
                stringResource(Res.string.prefetch_summary_status),
                PrefetchDisplayStatus.Paused(PrefetchPauseReason.NETWORK).label(),
            )
            DetailRow(stringResource(Res.string.prefetch_summary_last_run), "3 hours ago")
            DetailRow(
                stringResource(Res.string.prefetch_summary_warmed),
                stringResource(Res.string.prefetch_summary_warmed_value, 18, 25),
            )
            DetailRow(stringResource(Res.string.prefetch_summary_scheduled_run), "7 hours ago")
        }
        conditionRows.forEach { ConditionRow(it) }
        pendingRows.forEach { ConversationRow(it) }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailRow(
                stringResource(Res.string.prefetch_cache_messages),
                stringResource(Res.string.prefetch_cache_messages_value, 12_480),
            )
            DetailRow(stringResource(Res.string.prefetch_cache_images), formatBytes(48_600_000))
        }
    }
}

private val conditionRows = listOf(
    PrefetchConditionRow(PrefetchCondition.ENABLED, met = true),
    PrefetchConditionRow(PrefetchCondition.APP_AVAILABLE, met = true),
    PrefetchConditionRow(PrefetchCondition.NETWORK, met = false),
    PrefetchConditionRow(PrefetchCondition.POWER, met = false),
    PrefetchConditionRow(PrefetchCondition.IDLE, met = true),
    PrefetchConditionRow(PrefetchCondition.SERVER, met = true),
)

private val pendingRows = listOf(
    PrefetchConversationStatus("a", "Itinerary for the Lisbon trip", pinned = true, warmedAt = null, isCurrent = false),
    PrefetchConversationStatus("b", "Refactor the sync worker", pinned = false, warmedAt = null, isCurrent = false),
)

@Composable
private fun PrefetchSettingsHost() {
    PrefetchSettingsSection(
        prefetchEnabled = true,
        prefetchOnMeteredEnabled = false,
        prefetchAttachmentsEnabled = true,
        prefetchAttachmentsSupported = true,
        prefetchDepth = 25,
        status = PrefetchDisplayStatus.Paused(PrefetchPauseReason.POWER),
        warmedCount = 18,
        eligibleCount = 25,
        lastRunLabel = "3 hours ago",
        onPrefetchEnabledChange = {},
        onPrefetchOnMeteredChange = {},
        onPrefetchAttachmentsChange = {},
        onPrefetchDepthChange = {},
        onActivityClick = {},
    )
}

@Composable
private fun AboutInfoHost() {
    AboutInfo(
        serverUrl = "https://chat.example.com",
        serverTrustedViaUserCa = false,
        appVersion = "2026.10.17",
        gitSha = "e5932e9f3",
        serverVersion = null,
        buildInfo = BuildInfo(commitShort = "4f1c2ab", branch = "main", buildDate = "2026-10-01"),
    )
}

@Composable
private fun DataButtonsHost() {
    Column {
        DataSettingsSection(
            isClearing = false,
            onClearAllChats = {},
            archiveAllSupported = true,
            isArchivingAll = false,
            onArchiveAllChats = {},
            onViewArchive = {},
            logsBufferBytes = 0,
            isLogsExporting = false,
            isLogsClearing = false,
            onExportLogs = {},
            onClearLogs = {},
        )
        DataExtraActions(
            onArtifactShortcutsClick = {},
            onClearCacheClick = {},
            isCacheClearing = false,
            cacheSizeBytes = null,
        )
    }
}

@Composable
private fun NudgeHost() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NudgePreview(threshold = 80)
        NudgePreview(threshold = 0)
    }
}
