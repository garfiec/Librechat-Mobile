package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.WhatsNewViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Release notes for every release newer than the installed build, rendered natively from GitHub. */
@Composable
fun WhatsNewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhatsNewViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WhatsNewContent(
        uiState = uiState,
        installedVersion = viewModel.installedVersion,
        channel = viewModel.installChannel,
        onNavigateBack = onNavigateBack,
        onOpenInstaller = viewModel::openInstaller,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
