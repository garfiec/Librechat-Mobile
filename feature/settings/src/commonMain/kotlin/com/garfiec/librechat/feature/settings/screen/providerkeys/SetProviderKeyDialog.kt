package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel
import org.jetbrains.compose.resources.StringResource
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
