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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_save
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyUiState
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The key sheet for one endpoint. The header shows the list row's key state ([rowKeyState],
 * [rowFetchFailed], [rowRetrying]); the list re-reads it (on resume, after a save or revoke, on
 * [onRetryKeyState]), so the sheet never fetches it itself.
 *
 * **Leaving the app never closes or resets it**: the user is likely away copying the key. Nothing
 * here reacts to pause or resume except that ON_PAUSE re-masks revealed secrets. ON_PAUSE, not
 * ON_STOP: on iOS the stop comes with the scene entering the background, when Compose has already
 * stopped drawing, so the masked frame would never reach the app-switcher snapshot; the pause comes
 * from the scene resigning active, while frames are still drawn. The draft lives in the ViewModel's
 * saved state, so after process death the sheet comes back as it was left (masked). Only an
 * explicit open ([openRequest]) starts blank.
 *
 * While the draft holds input, or a save or revoke is in flight, every way out (swipe, scrim, back,
 * Cancel) keeps the sheet up: a draft asks before it is discarded, an in-flight request is waited
 * out. While a secret is shown the sheet is `secure`, out of screenshots and the recents snapshot.
 */
@Composable
fun SetProviderKeyDialog(
    endpointName: String,
    openRequest: Long,
    rowKeyState: KeyState,
    rowFetchFailed: Boolean,
    rowRetrying: Boolean,
    onRetryKeyState: () -> Unit,
    onDismiss: () -> Unit,
    onMutationSuccess: (endpointName: String, keyState: KeyState, message: StringResource) -> Unit,
    onShowMessage: (StringResource) -> Unit,
) {
    // One VM per endpoint name, reused across opens — bounded by the number of user-provide
    // endpoints (~5-10 in practice). The VM holds the draft in its SavedStateHandle.
    val viewModel: SetProviderKeyViewModel = koinViewModel(
        key = "ProviderKeyVm:$endpointName",
    ) { parametersOf(endpointName) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Not saved: a secret comes back masked after process death, and a new open starts masked.
    var revealed by remember(endpointName, openRequest) { mutableStateOf(emptySet<ProviderKeyField>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { revealed = emptySet() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val focusRequesters = remember { ProviderKeyField.entries.associateWith { FocusRequester() } }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnMutationSuccess by rememberUpdatedState(onMutationSuccess)
    val currentOnShowMessage by rememberUpdatedState(onShowMessage)

    // Keyed on the open request, so it fires on an explicit open and on a restoration, never on a
    // recomposition. The VM tells the two apart and resets the form only on a new open. Composed
    // before the result and effect handlers so a result left from the previous open is dropped
    // first, and a restoration is never taken for a new open.
    val currentRowKeyState by rememberUpdatedState(rowKeyState)
    val currentRowFetchFailed by rememberUpdatedState(rowFetchFailed)
    val currentOpenRequest by rememberUpdatedState(openRequest)
    LaunchedEffect(viewModel, openRequest) {
        viewModel.onOpened(openRequest, currentRowKeyState, currentRowFetchFailed)
    }
    LaunchedEffect(viewModel, rowKeyState, rowFetchFailed) { viewModel.onRowKeyState(rowKeyState, rowFetchFailed) }

    // A succeeded Save or Revoke is state, so a sheet recreated before it got to it (rotation, a
    // fold) acts on it then. Nothing in here suspends: once the VM has cleared the result, the row
    // refresh and the close can't be cancelled away. The VM says whether to close (not for a result
    // of an earlier open); the list decides where the message shows (its own snackbar, or the chat
    // it returns to).
    val pendingResult = state.pendingResult
    LaunchedEffect(viewModel, pendingResult) {
        val result = pendingResult ?: return@LaunchedEffect
        if (viewModel.onMutationResult(result, currentOpenRequest)) {
            currentOnMutationSuccess(endpointName, result.keyState, result.message)
            currentOnDismiss()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetProviderKeyEffect.ShowMessage -> currentOnShowMessage(effect.message)
                // The input may not be composed (the sheet still settling); then there is nothing to focus.
                is SetProviderKeyEffect.FocusField ->
                    runCatching { focusRequesters.getValue(effect.field).requestFocus() }
            }
        }
    }

    val currentState by rememberUpdatedState(state)
    val requestDismiss: () -> Unit = remember {
        {
            when {
                currentState.isBusy -> Unit
                currentState.isDirty -> confirmDiscard = true
                else -> currentOnDismiss()
            }
        }
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = requestDismiss,
        interceptDismiss = state.isDirty || state.isBusy,
        modifier = Modifier.clearFocusOnTap(),
        skipPartiallyExpanded = true,
        dragHandle = { LowProfileDragHandle() },
        secure = revealed.isNotEmpty(),
    ) {
        // Constructed once at the dialog level, so the form receives a stable callbacks instance
        // instead of the VM.
        val launchGoogleFilePicker = rememberProviderKeyFilePicker(
            onFileRead = viewModel::onGoogleServiceKeyFileRead,
        )
        val callbacks = remember(viewModel, launchGoogleFilePicker) {
            ProviderKeyFormCallbacks(onFieldChange = viewModel::updateField, launchGoogleFilePicker = launchGoogleFilePicker)
        }
        SetProviderKeySheetContent(
            state = state,
            callbacks = callbacks,
            fieldStates = ProviderKeyFieldStates(
                missing = state.missingFields,
                revealed = revealed,
                onRevealedChange = { field, show -> revealed = if (show) revealed + field else revealed - field },
                focusRequesters = focusRequesters,
                onDone = viewModel::save,
            ),
            onSelectExpiry = viewModel::selectExpiry,
            onRevoke = viewModel::revoke,
            onCancel = requestDismiss,
            onSave = viewModel::save,
            rowRetrying = rowRetrying,
            onRetryKeyState = onRetryKeyState,
        )
    }

    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                revealed = emptySet()
                viewModel.discardDraft()
                onDismiss()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/**
 * The sheet's body, apart from the sheet so previews can render it: the endpoint with its live
 * status, the form, the expiry of the key Save will store, why the last Save or Revoke failed, and
 * the actions.
 */
@Composable
internal fun SetProviderKeySheetContent(
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
            iconUrl = state.iconUrl,
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
