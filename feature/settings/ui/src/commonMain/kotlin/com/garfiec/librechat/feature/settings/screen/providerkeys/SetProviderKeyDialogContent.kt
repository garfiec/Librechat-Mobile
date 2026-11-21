package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_save
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The sheet's body, apart from the sheet so previews can render it: the endpoint with its live
 * status, the form, the expiry of the key Save will store, why the last Save or Revoke failed, and
 * the actions.
 */
@Composable
fun SetProviderKeySheetContent(
    state: SetProviderKeyUiState,
    callbacks: ProviderKeyFormCallbacks,
    fieldStates: ProviderKeyFieldStates,
    onSelectExpiry: (ProviderKeyExpiry) -> Unit,
    onRevoke: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    rowRetrying: Boolean,
    onRetryKeyState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(state, retrying = rowRetrying, onRetryKeyState = onRetryKeyState)
        if (state.awaitingConfig) {
            // Restored before the endpoint configs reloaded: which form the draft goes on isn't
            // known yet. The VM holds the draft and shows it once they arrive.
            Box(Modifier.fillMaxWidth().heightIn(min = AwaitingFormHeight), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator()
            }
        } else {
            // Fields stay enabled while busy: disabling drops focus, which closes the keyboard and
            // the sheet with it mid-save. The VM refuses a second save or a revoke until the first
            // answers.
            ProviderKeyForm(
                form = state.form,
                displayLabel = state.displayLabel,
                callbacks = callbacks,
                fieldStates = fieldStates,
            )
        }
        ExpiryChips(selected = state.expiry, onSelect = onSelectExpiry)
        state.submitError?.let { error ->
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        ProviderKeyActions(
            showRevoke = state.canRevoke,
            isRevoking = state.isRevoking,
            isSaving = state.isSaving,
            resultPending = state.resultPending,
            enabled = !state.awaitingConfig,
            onRevoke = onRevoke,
            onCancel = onCancel,
            onSave = onSave,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The endpoint as its list row shows it: icon, name and the same live status line, here with the
 * full expiry (wrapping onto as many lines as it needs rather than cut) and a retry when the key
 * couldn't be checked. As on the card, the retry stays put while it runs and its answer, and only
 * that, is read out.
 */
@Composable
private fun SheetHeader(state: SetProviderKeyUiState, retrying: Boolean, onRetryKeyState: () -> Unit) {
    val announcement = rememberRetryAnnouncement(retrying = retrying)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EndpointIcon(
            endpointName = state.endpointName,
            iconUrl = rememberJoinedIconUrl(state.iconUrl),
            provider = state.iconProvider,
            size = HeaderIconSize,
            glyphTint = MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.displayLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            // What a multi-field form needs, which the list's one-line cards have no room for.
            ProviderKeyStatusLine(
                keyState = state.rowKeyState,
                fetchFailed = state.rowFetchFailed && !retrying,
                needs = state.needs,
                wrap = true,
                announceChanges = announcement.active,
            )
        }
        if (state.rowFetchFailed) {
            ProviderKeyRetryButton(
                endpointLabel = state.displayLabel,
                retrying = retrying,
                onRetry = {
                    announcement.onRetry()
                    onRetryKeyState()
                },
            )
        }
    }
}

/**
 * Revoke on its own at the start, in the error colour, only when a key is (or may be) stored;
 * Cancel and Save at the end. Each button is its own item of the flow, so when they don't fit (a
 * narrow phone, a large font, a long translation) Save drops to a line of its own instead of being
 * squeezed. All three wait while a save or revoke is in flight or its [resultPending] (the sheet is
 * about to close); Revoke and Save also while not [enabled] (the form isn't known yet).
 */
@Composable
internal fun ProviderKeyActions(
    showRevoke: Boolean,
    isRevoking: Boolean,
    isSaving: Boolean,
    onRevoke: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    resultPending: Boolean = false,
    enabled: Boolean = true,
) {
    val busy = isRevoking || isSaving || resultPending
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRevoke) {
            AdaptiveTextButton(
                onClick = onRevoke,
                enabled = !busy && enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isRevoking) {
                    ButtonProgress()
                }
                Text(stringResource(Res.string.provider_keys_revoke))
            }
            // Holds Revoke apart from Cancel and Save on a shared line.
            Spacer(modifier = Modifier.weight(1f))
        }
        AdaptiveOutlinedButton(onClick = onCancel, enabled = !busy) {
            Text(stringResource(Res.string.action_cancel))
        }
        AdaptiveButton(onClick = onSave, enabled = !busy && enabled) {
            if (isSaving) {
                ButtonProgress()
            }
            Text(stringResource(Res.string.action_save))
        }
    }
}

/** The spinner a busy button shows before its label. */
@Composable
internal fun ButtonProgress() {
    AdaptiveCircularProgressIndicator(
        modifier = Modifier.padding(end = 8.dp).size(16.dp),
        strokeWidth = 2.dp,
    )
}

/** The lifetime of the key Save stores; the stored key's own expiry is in the header. */
@Composable
private fun ExpiryChips(
    selected: ProviderKeyExpiry,
    onSelect: (ProviderKeyExpiry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.provider_keys_expiry_label),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderKeyExpiry.entries.forEach { entry ->
                val isSelected = entry == selected
                AdaptiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelect(entry) },
                    label = { Text(stringResource(entry.label), maxLines = 1) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val HeaderIconSize = 40.dp

private val AwaitingFormHeight = 120.dp
