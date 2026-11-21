package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.UpToTwoColumns
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.resources.provider_keys_empty_desc
import com.garfiec.librechat.feature.settings.resources.provider_keys_empty_title
import com.garfiec.librechat.feature.settings.resources.provider_keys_load_failed_desc
import com.garfiec.librechat.feature.settings.resources.provider_keys_load_failed_title
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_button
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_confirm
import com.garfiec.librechat.feature.settings.resources.provider_keys_summary
import com.garfiec.librechat.feature.settings.resources.provider_keys_summary_note
import com.garfiec.librechat.feature.settings.screen.fullWidthItem
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeysUiState
import com.garfiec.librechat.feature.settings.state.providerkeys.liveKeyCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The page below the top bar for [uiState]: the load error, the empty state, or the summary, the
 * provider cards (placeholders on first load) and Revoke all. Two columns from 600dp.
 */
@Suppress("LongParameterList")
@Composable
internal fun ProviderKeysContent(
    uiState: ProviderKeysUiState,
    contentPadding: PaddingValues,
    onOpen: (endpointName: String) -> Unit,
    onRetryRow: (endpointName: String) -> Unit,
    onRetryLoad: () -> Unit,
    onRevokeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstLoad = uiState.isLoading && uiState.entries.isEmpty()
    when {
        uiState.loadError -> ProviderKeysLoadError(onRetry = onRetryLoad, modifier = modifier.padding(contentPadding))
        !firstLoad && uiState.entries.isEmpty() -> ProviderKeysEmpty(modifier = modifier.padding(contentPadding))
        else -> {
            val minCardWidth = ProviderKeyCardMinWidth * LocalDensity.current.fontScale
            LazyVerticalGrid(
                columns = remember(minCardWidth) { UpToTwoColumns(minCardWidth) },
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fullWidthItem(key = "summary") {
                    ProviderKeysSummaryHeader(entries = uiState.entries, counting = firstLoad || uiState.entries.any { it.isChecking })
                }
                if (firstLoad) {
                    items(count = PLACEHOLDER_COUNT, key = { "placeholder_$it" }, contentType = { "placeholder" }) {
                        ProviderKeyCardPlaceholder()
                    }
                } else {
                    items(items = uiState.entries, key = { it.endpointName }, contentType = { "provider_key" }) { entry ->
                        ProviderKeyCard(
                            entry = entry,
                            onClick = { onOpen(entry.endpointName) },
                            onRetry = { onRetryRow(entry.endpointName) },
                        )
                    }
                }
                if (uiState.canRevokeAll) {
                    fullWidthItem(key = "revoke_all") { RevokeAllButton(onClick = onRevokeAll) }
                }
            }
        }
    }
}

/**
 * "3 of 5 keys set." and how keys behave. The count is the rows' own (a key the ticker has run out
 * reads as not set in both), and waits behind a placeholder until every row has answered.
 */
@Composable
private fun ProviderKeysSummaryHeader(entries: List<ProviderKeyEntry>, counting: Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val style = MaterialTheme.typography.bodyLarge
        val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
        if (counting) {
            Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
                CheckingPlaceholderBar(widthFraction = SUMMARY_PLACEHOLDER_FRACTION)
            }
        } else {
            val now = LocalRelativeTimeReference.current.now
            val set = remember(entries, now) { entries.liveKeyCount(now) }
            Text(
                text = pluralStringResource(Res.plurals.provider_keys_summary, entries.size, set, entries.size),
                style = style,
            )
        }
        Text(
            text = stringResource(Res.string.provider_keys_summary_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Quiet and at the end of the list, away from the rows: it removes every key at once. */
@Composable
private fun RevokeAllButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        AdaptiveTextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(Res.string.provider_keys_revoke_all_button), textAlign = TextAlign.Center)
        }
    }
}

/** Stays up with a spinner until the server answers; both buttons are off meanwhile. */
@Composable
internal fun RevokeAllDialog(
    isRevoking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.provider_keys_revoke_all_button)) },
        text = { Text(stringResource(Res.string.provider_keys_revoke_all_confirm)) },
        confirmButton = {
            AdaptiveTextButton(
                onClick = onConfirm,
                enabled = !isRevoking,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isRevoking) {
                    ButtonProgress()
                }
                Text(stringResource(Res.string.provider_keys_revoke))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss, enabled = !isRevoking) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/** No reachable endpoint takes a user-provided key. */
@Composable
internal fun ProviderKeysEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(Res.string.provider_keys_empty_title),
        description = stringResource(Res.string.provider_keys_empty_desc),
        icon = Icons.Outlined.Key,
        modifier = modifier.padding(horizontal = 32.dp),
    )
}

/** The endpoint config couldn't be loaded: nothing to list, and a retry. */
@Composable
internal fun ProviderKeysLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = stringResource(Res.string.provider_keys_load_failed_title),
        description = stringResource(Res.string.provider_keys_load_failed_desc),
        icon = Icons.Outlined.CloudOff,
        modifier = modifier.padding(horizontal = 32.dp),
        action = {
            AdaptiveButton(onClick = onRetry) {
                Text(stringResource(Res.string.action_retry))
            }
        },
    )
}

/** Two columns once each gets this much: from 600dp wide, after the 16dp gutters and 8dp gap. */
private val ProviderKeyCardMinWidth = 280.dp

private const val PLACEHOLDER_COUNT = 4
private const val SUMMARY_PLACEHOLDER_FRACTION = 0.5f
