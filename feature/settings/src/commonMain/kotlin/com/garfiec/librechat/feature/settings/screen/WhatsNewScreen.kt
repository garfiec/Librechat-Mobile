package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.update.InstallChannel
import com.garfiec.librechat.core.model.AppRelease
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.util.rememberUriOpener
import com.garfiec.librechat.feature.settings.viewmodel.WhatsNewUiState
import com.garfiec.librechat.feature.settings.viewmodel.WhatsNewViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Release notes for every release newer than the installed build, rendered natively from GitHub. */
@Composable
fun WhatsNewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhatsNewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val content = uiState as? WhatsNewUiState.Content

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.whats_new_title)),
                    actions = emptyList(),
                ),
            )
        },
        bottomBar = {
            if (content != null) {
                UpdateActions(
                    latest = content.releases.first(),
                    channel = viewModel.installChannel,
                    onOpenInstaller = viewModel::openInstaller,
                )
            }
        },
    ) { padding ->
        val bodyModifier = Modifier.fillMaxSize().padding(padding)
        when (val state = uiState) {
            WhatsNewUiState.Loading -> LoadingIndicator(modifier = bodyModifier)
            WhatsNewUiState.UpToDate -> EmptyState(
                title = stringResource(Res.string.update_up_to_date),
                description = stringResource(Res.string.whats_new_installed, viewModel.installedVersion),
                icon = Icons.Default.CheckCircle,
                modifier = bodyModifier,
            )
            WhatsNewUiState.Error -> Column(modifier = bodyModifier.padding(16.dp)) {
                ErrorBanner(
                    message = stringResource(Res.string.whats_new_error),
                    onRetry = viewModel::retry,
                    retryLabel = stringResource(Res.string.action_retry),
                )
            }
            is WhatsNewUiState.Content -> ReleaseList(
                releases = state.releases,
                installedVersion = viewModel.installedVersion,
                channel = viewModel.installChannel,
                modifier = bodyModifier,
            )
        }
    }
}

@Composable
private fun ReleaseList(
    releases: List<AppRelease>,
    installedVersion: String,
    channel: InstallChannel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item(key = "summary") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(Res.string.update_available, releases.first().version),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.whats_new_installed, installedVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channelHint(channel)?.let { hint ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                AdaptiveDivider()
            }
        }
        items(releases, key = { it.tag }) { release ->
            ReleaseNotes(release)
        }
    }
}

@Composable
private fun channelHint(channel: InstallChannel): String? = when (channel) {
    InstallChannel.OBTAINIUM -> stringResource(Res.string.whats_new_hint_obtainium)
    InstallChannel.FDROID -> stringResource(Res.string.whats_new_hint_fdroid)
    InstallChannel.DIRECT -> null
}

@Composable
private fun ReleaseNotes(release: AppRelease) {
    Column {
        ReleaseNotesBody(release)
        AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun UpdateActions(
    latest: AppRelease,
    channel: InstallChannel,
    onOpenInstaller: () -> Boolean,
) {
    val uriOpener = rememberUriOpener()
    val download = { uriOpener.open(latest.apkUrl ?: latest.htmlUrl) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveButton(
                onClick = { if (channel == InstallChannel.DIRECT || !onOpenInstaller()) download() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when (channel) {
                            InstallChannel.OBTAINIUM -> Res.string.whats_new_open_obtainium
                            InstallChannel.FDROID -> Res.string.whats_new_open_fdroid
                            InstallChannel.DIRECT -> Res.string.whats_new_download_apk
                        },
                    ),
                )
            }
            AdaptiveOutlinedButton(
                onClick = { uriOpener.open(latest.htmlUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.whats_new_view_on_github))
            }
        }
    }
}
