package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ApiKey
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysLoadFailure
import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysUiState
import org.jetbrains.compose.resources.stringResource

/** The API keys list, its create button and its delete and create dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysContent(
    uiState: ApiKeysUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onShowCreateDialog: () -> Unit,
    onCreateKey: (name: String) -> Unit,
    onDismissCreateDialog: () -> Unit,
    onDeleteKey: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyToDelete by remember { mutableStateOf<ApiKey?>(null) }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_api_keys)),
                    actions = emptyList(),
                ),
            )
        },
        floatingActionButton = {
            if (uiState.loadFailure == null || uiState.keys.isNotEmpty()) {
                AdaptiveFloatingActionButton(onClick = onShowCreateDialog) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cd_create_api_key),
                    )
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val loadFailure = uiState.loadFailure
            when {
                uiState.isLoading && uiState.keys.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveCircularProgressIndicator()
                    }
                }
                !uiState.isLoading && loadFailure != null && uiState.keys.isEmpty() -> {
                    ApiKeysLoadFailureState(failure = loadFailure, onRetry = onRetry)
                }
                !uiState.isLoading && uiState.keys.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(Res.string.no_api_keys),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(Res.string.no_api_keys_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.keys,
                            key = { it.id ?: it.name },
                            contentType = { "api_key" },
                        ) { apiKey ->
                            ApiKeyCard(
                                apiKey = apiKey,
                                isDeleting = uiState.deletingKeyId == apiKey.id,
                                onDeleteClick = { keyToDelete = apiKey },
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    keyToDelete?.let { key ->
        AdaptiveAlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text(stringResource(Res.string.dialog_title_delete_api_key)) },
            text = {
                Text(stringResource(Res.string.dialog_delete_api_key_message, key.name))
            },
            confirmButton = {
                AdaptiveTextButton(
                    onClick = {
                        val id = key.id
                        keyToDelete = null
                        if (id != null) {
                            onDeleteKey(id)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { keyToDelete = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Create dialog
    if (uiState.showCreateDialog) {
        ApiKeyCreateDialog(
            createdKey = uiState.createdKey,
            isCreating = uiState.isCreating,
            onCreateKey = onCreateKey,
            onDismiss = onDismissCreateDialog,
        )
    }
}

@Composable
internal fun ApiKeyCard(
    apiKey: ApiKey,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
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
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apiKey.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val keyDisplay = apiKey.keyValue ?: apiKey.keyPrefix
                if (keyDisplay != null) {
                    val truncated = if (apiKey.keyValue != null && keyDisplay.length > 12) {
                        keyDisplay.take(8) + "..." + keyDisplay.takeLast(4)
                    } else {
                        keyDisplay + "..."
                    }
                    Text(
                        text = truncated,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val createdAt = apiKey.createdAt
                if (createdAt != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.created_prefix, formatDate(createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isDeleting) {
                AdaptiveCircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.cd_delete_api_key),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatDate(dateString: String): String {
    // Simple date formatting - show the date portion
    return dateString.substringBefore("T").ifBlank { dateString }
}

/** The list couldn't load: "not available" for a server without the route, else the error and Retry. */
@Composable
private fun ApiKeysLoadFailureState(failure: ApiKeysLoadFailure, onRetry: () -> Unit) {
    when (failure) {
        ApiKeysLoadFailure.NotSupported -> EmptyState(
            title = stringResource(Res.string.api_keys_not_available),
            description = stringResource(Res.string.api_keys_not_supported),
            icon = Icons.Default.Key,
        )
        is ApiKeysLoadFailure.Failed -> EmptyState(
            title = stringResource(Res.string.api_keys_load_failed),
            description = failure.message,
            icon = Icons.Default.Key,
            action = {
                AdaptiveOutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            },
        )
    }
}
