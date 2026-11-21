package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.PasskeysNotice
import com.garfiec.librechat.feature.settings.viewmodel.PasskeysViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › Account › Passkeys: the account's passkeys, with rename and remove. Adding a passkey
 * isn't offered (the server refuses a native app's WebAuthn origin); a line points to the browser.
 */
@Composable
fun PasskeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasskeysViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-read on every return after the first, so a passkey added or removed on the web shows up.
    OnResume(onReturn = viewModel::refreshOnReturn)

    val alreadyRemoved = stringResource(Res.string.passkeys_already_removed)
    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        when (notice) {
            PasskeysNotice.AlreadyRemoved -> snackbarHostState.showSnackbar(alreadyRemoved)
        }
        viewModel.consumeNotice()
    }

    val actions = remember(viewModel) {
        PasskeysActions(
            load = viewModel::load,
            startRename = viewModel::startRename,
            onRenameChanged = viewModel::onRenameChanged,
            saveRename = viewModel::saveRename,
            dismissRename = viewModel::dismissRename,
            startRemove = viewModel::startRemove,
            onRemovePasswordChanged = viewModel::onRemovePasswordChanged,
            confirmRemove = viewModel::confirmRemove,
            dismissRemove = viewModel::dismissRemove,
        )
    }
    PasskeysScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        actions = actions,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
