package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.auth.viewmodel.TermsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The server's terms, which the deployment requires the user to accept (`modalAcceptance`). Shown
 * only when acceptance is required, so it cannot be dismissed: the top-bar arrow, system back and the
 * predictive back gesture all mean [onDecline], as the explicit Decline button does. Upstream's
 * decline signs out; the shell does the same for this account only.
 *
 * [declining] is the shell's decline in flight: the screen stays up through its revoke call, and
 * Decline, Accept and back do nothing until it ends.
 */
@Composable
fun TermsScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    declining: Boolean,
    modifier: Modifier = Modifier,
    viewModel: TermsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnAccepted by rememberUpdatedState(onAccept)

    LaunchedEffect(uiState.isAccepted) {
        if (uiState.isAccepted) {
            viewModel.consumeAccepted()
            currentOnAccepted()
        }
    }

    TermsContent(
        uiState = uiState,
        declining = declining,
        onAcceptTerms = viewModel::acceptTerms,
        onDecline = onDecline,
        onRetry = viewModel::loadTerms,
        modifier = modifier,
    )
}
