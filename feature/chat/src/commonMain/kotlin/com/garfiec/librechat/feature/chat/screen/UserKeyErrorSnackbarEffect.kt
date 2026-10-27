package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.set_api_key_action
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Collects [ChatViewModel.userKeyErrors] and surfaces a snackbar naming the endpoint, with an
 * "Add key" action to Settings → Provider API Keys. Messages resolve through the suspend
 * `getString` overload because String.format isn't available in Kotlin/Native.
 *
 * The endpoint name from the typed error is forwarded so the destination can auto-open
 * the Set Key dialog for that endpoint; null endpoint falls through to the list view.
 */
@Composable
fun UserKeyErrorSnackbarEffect(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val addKeyAction = stringResource(Res.string.set_api_key_action)
    val currentAddKeyAction by rememberUpdatedState(addKeyAction)
    val currentOnNavigateToProviderKeys by rememberUpdatedState(onNavigateToProviderKeys)
    val currentDateTimeFormat by rememberUpdatedState(LocalDateTimeFormat.current)
    LaunchedEffect(viewModel) {
        viewModel.userKeyErrors.collect { error ->
            val label = error.endpointLabel(viewModel.uiState.value.selection.endpointConfigs)
            val message = userKeyErrorMessage(error, label, currentDateTimeFormat)
            val result = snackbarHostState.showSnackbar(
                message = message.resolve(),
                actionLabel = currentAddKeyAction,
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentOnNavigateToProviderKeys(error.endpoint)
            }
        }
    }
}
