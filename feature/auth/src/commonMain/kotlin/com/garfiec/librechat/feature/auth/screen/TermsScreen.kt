package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.TermsViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import org.jetbrains.compose.resources.stringResource
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val decline = declineUnlessBusy(isAccepting = uiState.isAccepting, isDeclining = declining, onDecline = onDecline)

    // Always enabled: a disabled handler would let the system pop the screen, dismissing terms the
    // server requires. While busy it takes back and does nothing.
    PlatformBackHandler(enabled = true, onBack = decline)

    LaunchedEffect(uiState.isAccepted) {
        if (uiState.isAccepted) {
            viewModel.consumeAccepted()
            currentOnAccepted()
        }
    }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.terms_decline), decline),
                    title = BarTitle(stringResource(Res.string.terms_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                !uiState.termsLoaded && uiState.error == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AdaptiveCircularProgressIndicator()
                    }
                }
                !uiState.termsLoaded -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            AdaptiveOutlinedButton(onClick = viewModel::loadTerms) {
                                Text(stringResource(Res.string.retry))
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = uiState.title ?: stringResource(Res.string.terms_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .semantics { heading() },
                    )

                    TermsBody(
                        markdown = uiState.termsText,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                    )

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AdaptiveOutlinedButton(
                            onClick = decline,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isAccepting && !declining,
                        ) {
                            Text(stringResource(Res.string.terms_decline))
                        }
                        AdaptiveButton(
                            onClick = viewModel::acceptTerms,
                            modifier = Modifier.weight(1f),
                            enabled = !declining,
                            loading = uiState.isAccepting,
                        ) {
                            Text(stringResource(if (uiState.isAccepting) Res.string.progress_accepting else Res.string.i_accept))
                        }
                    }
                }
            }
        }
    }
}

/** The server's terms as Markdown, as web renders them; its "no content" line when it sent none. */
@Composable
private fun TermsBody(markdown: String, modifier: Modifier = Modifier) {
    if (markdown.isBlank()) {
        Text(
            text = stringResource(Res.string.terms_no_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    // Parsed synchronously so the scroll column measures the whole body on its first frame.
    val parsed by rememberMarkdownState(content = markdown, immediate = true).state.collectAsState()
    Box(modifier) {
        Markdown(
            state = parsed,
            colors = markdownColor(),
            typography = markdownTypography(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What back, predictive back, the top-bar arrow and Decline all do: decline, unless an accept or a
 * decline is already in flight, when they do nothing.
 */
internal fun declineUnlessBusy(isAccepting: Boolean, isDeclining: Boolean, onDecline: () -> Unit): () -> Unit = {
    if (!isAccepting && !isDeclining) onDecline()
}
