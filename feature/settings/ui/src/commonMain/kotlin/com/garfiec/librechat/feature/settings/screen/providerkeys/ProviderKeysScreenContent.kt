package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.cd_back
import com.garfiec.librechat.feature.settings.resources.provider_keys_title
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeysUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The Provider keys page for [uiState]: bar, list and the revoke-all prompt. [setKeyDialog] draws the
 * set-key sheet for the endpoint in [ProviderKeysUiState.pendingDialogEndpoint].
 */
@Composable
fun ProviderKeysScreenContent(
    uiState: ProviderKeysUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpen: (endpointName: String) -> Unit,
    onRetryRow: (endpointName: String) -> Unit,
    onRetryLoad: () -> Unit,
    onRevokeAll: () -> Unit,
    onConfirmRevokeAll: () -> Unit,
    onDismissRevokeAll: () -> Unit,
    modifier: Modifier = Modifier,
    setKeyDialog: @Composable (endpointName: String) -> Unit = {},
) {
    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.provider_keys_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        ProviderKeysContent(
            uiState = uiState,
            contentPadding = innerPadding,
            onOpen = onOpen,
            onRetryRow = onRetryRow,
            onRetryLoad = onRetryLoad,
            onRevokeAll = onRevokeAll,
        )
    }

    uiState.pendingDialogEndpoint?.let { setKeyDialog(it) }

    if (uiState.showRevokeAllConfirm) {
        RevokeAllDialog(
            isRevoking = uiState.isRevokingAll,
            onConfirm = onConfirmRevokeAll,
            onDismiss = onDismissRevokeAll,
        )
    }
}
