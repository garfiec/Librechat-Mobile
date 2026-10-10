package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.network.api.GitHubReleasesApi
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.util.rememberUriOpener
import com.garfiec.librechat.feature.settings.viewmodel.InstalledNotes
import com.garfiec.librechat.feature.settings.viewmodel.OlderStatus
import com.garfiec.librechat.feature.settings.viewmodel.ReleaseNotesViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Release notes for the installed build and the releases before it, rendered natively from GitHub. */
@Composable
fun ReleaseNotesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReleaseNotesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installed = uiState.installed

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.release_notes_title)),
                    actions = emptyList(),
                ),
            )
        },
        bottomBar = {
            GitHubAction(
                url = (installed as? InstalledNotes.Content)?.release?.htmlUrl
                    ?: "${GitHubReleasesApi.REPO_URL}/releases",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item(key = "installed_version") {
                Text(
                    text = stringResource(Res.string.whats_new_installed, viewModel.installedVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item(key = "installed_notes") {
                when (installed) {
                    InstalledNotes.Loading -> CenteredProgress()
                    InstalledNotes.NotFound -> Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.release_notes_not_found, viewModel.installedVersion),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(Res.string.release_notes_not_found_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InstalledNotes.Error -> ErrorBanner(
                        message = stringResource(Res.string.whats_new_error),
                        onRetry = viewModel::retryInstalled,
                        retryLabel = stringResource(Res.string.action_retry),
                        modifier = Modifier.padding(16.dp),
                    )
                    is InstalledNotes.Content -> ReleaseNotesBody(installed.release)
                }
            }
            if (uiState.older.isNotEmpty()) {
                item(key = "older_header") {
                    Column {
                        AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Text(
                            text = stringResource(Res.string.release_notes_previous),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            items(uiState.older, key = { it.tag }) { release ->
                CollapsedReleaseNotes(release)
            }
            item(key = "older_footer") {
                when (uiState.olderStatus) {
                    OlderStatus.LOADING -> if (installed != InstalledNotes.Loading) CenteredProgress()
                    OlderStatus.MORE -> AdaptiveOutlinedButton(
                        onClick = viewModel::loadOlder,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(Res.string.release_notes_load_older))
                    }
                    OlderStatus.ERROR -> ErrorBanner(
                        message = stringResource(Res.string.whats_new_error),
                        onRetry = viewModel::loadOlder,
                        retryLabel = stringResource(Res.string.action_retry),
                        modifier = Modifier.padding(16.dp),
                    )
                    OlderStatus.END -> Unit
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun GitHubAction(url: String) {
    val uriOpener = rememberUriOpener()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        AdaptiveOutlinedButton(
            onClick = { uriOpener.open(url) },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(stringResource(Res.string.whats_new_view_on_github))
        }
    }
}
