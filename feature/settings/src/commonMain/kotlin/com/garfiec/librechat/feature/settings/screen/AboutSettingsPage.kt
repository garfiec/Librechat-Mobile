package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.UpdateCheckViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › About: the app and server versions, the privacy policy and updates. */
@Composable
fun AboutSettingsPage(
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: GeneralSettingsViewModel = koinViewModel(),
    updateViewModel: UpdateCheckViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "about_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_about))
        }
        adaptiveSection {
            row(key = "about_info") {
                AboutInfo(
                    serverUrl = uiState.serverUrl,
                    appVersion = uiState.appVersion,
                    serverVersion = uiState.serverVersion,
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

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
internal fun AboutInfo(
    serverUrl: String,
    appVersion: String,
    serverVersion: String?,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AboutRow(stringResource(Res.string.app_version_label), appVersion)
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
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/** Whether [ServerDetails] has anything to show. */
internal fun hasServerDetails(gitSha: String, serverTrustedViaUserCa: Boolean, buildInfo: BuildInfo?): Boolean =
    gitSha.isNotBlank() || serverTrustedViaUserCa || buildInfo?.let {
        !(it.commitShort ?: it.commit).isNullOrBlank() || !it.branch.isNullOrBlank() || !it.buildDate.isNullOrBlank()
    } == true

/** Advanced › Server: the app's build, the server's build metadata (v0.8.6) and how its certificate is trusted. */
@Composable
internal fun ServerDetails(
    gitSha: String,
    serverTrustedViaUserCa: Boolean,
    buildInfo: BuildInfo?,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (gitSha.isNotBlank()) AboutRow(stringResource(Res.string.app_build_label), gitSha)
            val commit = buildInfo?.commitShort ?: buildInfo?.commit
            if (!commit.isNullOrBlank()) AboutRow(stringResource(Res.string.server_build_commit_label), commit)
            buildInfo?.branch?.takeIf { it.isNotBlank() }?.let { branch ->
                AboutRow(stringResource(Res.string.server_build_branch_label), branch)
            }
            buildInfo?.buildDate?.takeIf { it.isNotBlank() }?.let { buildDate ->
                AboutRow(stringResource(Res.string.server_build_date_label), buildDate)
            }
            if (serverTrustedViaUserCa) {
                Text(
                    text = stringResource(Res.string.server_user_ca_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
