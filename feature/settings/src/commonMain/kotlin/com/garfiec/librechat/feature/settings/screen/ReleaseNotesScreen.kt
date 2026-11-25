package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.network.api.GitHubReleasesApi
import com.garfiec.librechat.feature.settings.viewmodel.ReleaseNotesViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Release notes for the installed build and the releases before it, rendered natively from GitHub. */
@Composable
fun ReleaseNotesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReleaseNotesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReleaseNotesContent(
        uiState = uiState,
        installedVersion = viewModel.installedVersion,
        repoUrl = GitHubReleasesApi.REPO_URL,
        onNavigateBack = onNavigateBack,
        onRetryInstall = viewModel::retryInstalled,
        onLoadOlder = viewModel::loadOlder,
        modifier = modifier,
    )
}
