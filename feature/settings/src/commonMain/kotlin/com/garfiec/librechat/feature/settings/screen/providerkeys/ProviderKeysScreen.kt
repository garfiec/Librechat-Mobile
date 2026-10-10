package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.cd_back
import com.garfiec.librechat.feature.settings.resources.provider_keys_empty_desc
import com.garfiec.librechat.feature.settings.resources.provider_keys_empty_title
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_action
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_confirm
import com.garfiec.librechat.feature.settings.resources.provider_keys_subtitle
import com.garfiec.librechat.feature.settings.resources.provider_keys_title
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.ProviderKeysViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderKeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderKeysViewModel = koinViewModel(),
    /**
     * If non-null, auto-opens the [SetProviderKeyDialog] for this endpoint on
     * first composition. Used by the chat model-selector "Set API Key" CTA and
     * the chat-send `UserKeyError` snackbar to deep-link the user directly to
     * the right form. The keyed [LaunchedEffect] only fires when the value
     * changes, so a back/forward navigation re-opens the dialog as expected
     * without looping on dismissal.
     */
    pendingDialogEndpoint: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Note: cross-process key mutations (e.g. web client revokes a key while mobile is
    // backgrounded) are not refreshed on resume — same posture as the chat side. The
    // keyInvalidations SharedFlow + endpointConfigs collector cover all in-process flows.

    // Track whether the route arg has already triggered a dialog open. `rememberSaveable`
    // keyed on `pendingDialogEndpoint` survives rotation: same landing -> consumed flag
    // preserved (no reopen if user dismissed). Fresh deep-link to the same endpoint produces
    // a new NavBackStackEntry, which gives a fresh `rememberSaveable` slot and reopens.
    var consumed by rememberSaveable(pendingDialogEndpoint) { mutableStateOf(false) }
    LaunchedEffect(pendingDialogEndpoint) {
        if (pendingDialogEndpoint != null && !consumed) {
            viewModel.openDialog(pendingDialogEndpoint)
            consumed = true
        }
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }
    LaunchedEffect(uiState.transientMessage) {
        val msg = uiState.transientMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg)
        viewModel.consumeTransientMessage()
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.provider_keys_title)),
                    actions = if (uiState.entries.any { it.keyState !is KeyState.Unset }) {
                        listOf(
                            BarAction.Text(
                                id = "revoke_all",
                                label = stringResource(Res.string.provider_keys_revoke_all_action),
                                onClick = viewModel::showRevokeAllConfirm,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveCircularProgressIndicator()
                }
            }
            uiState.entries.isEmpty() -> {
                EmptyState(
                    title = stringResource(Res.string.provider_keys_empty_title),
                    description = stringResource(Res.string.provider_keys_empty_desc),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "subtitle") {
                        Text(
                            text = stringResource(Res.string.provider_keys_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(
                        items = uiState.entries,
                        key = { it.endpointName },
                        contentType = { "provider_key" },
                    ) { entry ->
                        ProviderKeyRow(
                            entry = entry,
                            onClick = { viewModel.openDialog(entry.endpointName) },
                        )
                    }
                }
            }
        }
    }

    val pending = uiState.pendingDialogEndpoint
    if (pending != null) {
        SetProviderKeyDialog(
            endpointName = pending,
            onDismiss = viewModel::dismissDialog,
            onMutationSuccess = viewModel::onChildKeyChanged,
            onShowMessage = viewModel::emitTransientMessage,
        )
    }

    if (uiState.showRevokeAllConfirm) {
        AdaptiveAlertDialog(
            onDismissRequest = viewModel::dismissRevokeAllConfirm,
            title = { Text(stringResource(Res.string.provider_keys_revoke_all)) },
            text = { Text(stringResource(Res.string.provider_keys_revoke_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::revokeAll,
                    enabled = !uiState.isRevokingAll,
                ) {
                    Text(stringResource(Res.string.provider_keys_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRevokeAllConfirm) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderKeyRow(
    entry: ProviderKeyEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.config.modelDisplayLabel ?: entry.endpointName,
                    style = MaterialTheme.typography.titleSmall,
                )
                KeyStateDisplay(entry.keyState)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
