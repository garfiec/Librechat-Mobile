package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shows [message] once per emission of [signal]. The text is read at show time, so a language
 * switch while the screen is up is not answered in the old language.
 */
@Composable
internal fun SignalSnackbarEffect(
    signal: Flow<*>,
    message: StringResource,
    snackbarHostState: SnackbarHostState,
) {
    val text by rememberUpdatedState(stringResource(message))
    LaunchedEffect(signal) {
        signal.collect { snackbarHostState.showSnackbar(message = text) }
    }
}
