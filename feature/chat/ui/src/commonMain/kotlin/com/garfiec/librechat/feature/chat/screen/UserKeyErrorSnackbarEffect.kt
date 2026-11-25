package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.set_api_key_action
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/**
 * Collects [userKeyErrors] and surfaces a snackbar naming the endpoint, with an
 * "Add key" action to Settings → Provider API Keys. Messages resolve through the suspend
 * `getString` overload because String.format isn't available in Kotlin/Native.
 *
 * The endpoint name from the typed error is forwarded so the destination can auto-open
 * the Set Key dialog for that endpoint; null endpoint falls through to the list view.
 */
@Composable
fun UserKeyErrorSnackbarEffect(
    userKeyErrors: Flow<UserKeyError>,
    /** The served endpoint configs at the time an error arrives, for its label. */
    endpointConfigs: () -> Map<String, EndpointConfig>,
    snackbarHostState: SnackbarHostState,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val addKeyAction = stringResource(Res.string.set_api_key_action)
    val currentAddKeyAction by rememberUpdatedState(addKeyAction)
    val currentOnNavigateToProviderKeys by rememberUpdatedState(onNavigateToProviderKeys)
    val currentDateTimeFormat by rememberUpdatedState(LocalDateTimeFormat.current)
    val currentEndpointConfigs by rememberUpdatedState(endpointConfigs)
    LaunchedEffect(userKeyErrors) {
        userKeyErrors.collect { error ->
            val label = error.endpointLabel(currentEndpointConfigs())
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
