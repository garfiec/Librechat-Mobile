package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.brand.TchatBrand
import com.garfiec.librechat.core.data.repository.HeaderWriteFailure
import com.garfiec.librechat.core.network.client.HeaderRejection
import com.garfiec.librechat.core.ui.components.CustomHeaderRow
import com.garfiec.librechat.core.ui.components.CustomHeaderRowError
import com.garfiec.librechat.core.ui.components.CustomHeadersEditor
import com.garfiec.librechat.core.ui.components.testTagsAsResourceIdSubtree
import com.garfiec.librechat.core.ui.resources.server_headers_load_error
import com.garfiec.librechat.core.ui.resources.server_headers_no_server
import com.garfiec.librechat.core.ui.resources.server_headers_save_error
import com.garfiec.librechat.core.ui.resources.server_headers_unverified_delete
import com.garfiec.librechat.feature.auth.resources.*
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.viewmodel.HeaderFieldError
import com.garfiec.librechat.feature.auth.viewmodel.ServerUrlViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.garfiec.librechat.core.ui.resources.Res as UiRes

@Composable
fun ServerUrlScreen(
    onServerValidate: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: ServerUrlViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnServerValidated by rememberUpdatedState(onServerValidate)

    LaunchedEffect(uiState.isValidated) {
        if (uiState.isValidated) {
            currentOnServerValidated()
            // Consume it, or backing out of the next screen lands here and is immediately thrown
            // forward again — this entry's ViewModel outlives the forward navigation.
            viewModel.consumeValidated()
        }
    }

    if (uiState.showHttpWarning) {
        HttpWarningDialog(
            onConfirm = viewModel::confirmHttpConnection,
            onDismiss = viewModel::dismissHttpWarning,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (uiState.autoConnecting) {
            // TCHAT: force-pinned build — there is no URL to enter, so this replaces the form
            // entirely rather than merely disabling it. `error` still comes from the same
            // doValidateAndConnect() path as the normal flow; only the affordance to fix it
            // differs (Retry instead of editing a field).
            AutoConnectingContent(
                error = uiState.error,
                onRetry = viewModel::validateAndConnect,
                modifier = Modifier.align(Alignment.Center),
            )
            BackAffordanceOverlay(onBack)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.connect_to_librechat),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.enter_server_url_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChanged,
                label = { Text(stringResource(Res.string.server_url_label)) },
                placeholder = { Text(stringResource(Res.string.server_url_placeholder)) },
                modifier = Modifier.fillMaxWidth().testTag("server_url_field"),
                singleLine = true,
                isError = uiState.error != null,
                supportingText = uiState.error?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                enabled = !uiState.isLoading,
            )

            // TCHAT: Tchat's nginx has no access gateway (Cloudflare Access or equivalent) in
            // front of it, so there is no server that would ever need a custom per-request
            // header — see TchatBrand.EXPOSE_PROVIDER_BACKEND_SETTINGS's kdoc. In practice this
            // whole screen never renders for Tchat anyway (ServerPinning.allowOverride is false in
            // every build; auto-connect skips straight past it), but the guard stays in case a
            // future non-Tchat build of this fork ever sets allowOverride true.
            if (TchatBrand.EXPOSE_PROVIDER_BACKEND_SETTINGS) {
                Spacer(modifier = Modifier.height(8.dp))

                CustomHeadersSection(
                    expanded = uiState.showAdvanced,
                    headers = uiState.customHeaders,
                    headerError = uiState.headerError,
                    // A save failure supersedes the load warning: it is the more recent, more actionable
                    // of the two, and the load warning is what the refused save was reacting to.
                    // Each case gets the same wording as its Settings counterpart: the two editors write
                    // to one store, so one failure must not be described two different ways.
                    storeWarning = when (uiState.headersSaveFailure) {
                        HeaderWriteFailure.UnverifiedDelete ->
                            stringResource(UiRes.string.server_headers_unverified_delete)
                        HeaderWriteFailure.NoServer ->
                            stringResource(UiRes.string.server_headers_no_server)
                        // NothingUsable is unreachable from here — Connect validates every row first —
                        // so it shares the generic message rather than getting a string of its own.
                        HeaderWriteFailure.StorageUnavailable, HeaderWriteFailure.NothingUsable ->
                            stringResource(UiRes.string.server_headers_save_error)
                        null -> stringResource(UiRes.string.server_headers_load_error)
                            .takeIf { uiState.headersLoadFailed }
                    },
                    enabled = !uiState.isLoading,
                    onToggle = viewModel::toggleAdvanced,
                    onNameChange = viewModel::onHeaderNameChanged,
                    onValueChange = viewModel::onHeaderValueChanged,
                    onAdd = viewModel::addHeaderRow,
                    onRemove = viewModel::removeHeaderRow,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::validateAndConnect,
                modifier = Modifier.fillMaxWidth().testTag("server_url_connect"),
                enabled = !uiState.isLoading && uiState.url.isNotBlank(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.server_url_connect))
                }
            }
        }

        BackAffordanceOverlay(onBack)
    }
}

/**
 * TCHAT: shown instead of the URL form on a force-pinned build. `error == null` is the ordinary
 * cold-start path (a brief branded spinner while the pinned connect resolves); non-null is an
 * offline/unreachable cold start, which needs an explicit way forward since there is no field for
 * the user to edit and no other affordance on this screen.
 */
@Composable
private fun AutoConnectingContent(
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = TchatBrand.APP_NAME,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (error == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

/**
 * Collapsed-by-default editor for the server's static gateway headers (issue #287).
 *
 * Pre-login only, and that placement is the point: a deployment behind Cloudflare Access (or
 * Authelia, Authentik, oauth2-proxy…) can't be reached *at all* without these, so a post-login
 * settings screen would be unreachable for exactly the users who need it.
 */
@Composable
private fun CustomHeadersSection(
    expanded: Boolean,
    headers: List<CustomHeaderRow>,
    headerError: HeaderFieldError?,
    /** Store-level warning (a read or a write that failed), as opposed to a rejected row. */
    storeWarning: String?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onNameChange: (Int, String) -> Unit,
    onValueChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.align(Alignment.Start).testTag("server_url_advanced_toggle"),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(Res.string.server_headers_advanced))
        }

        if (!expanded) return@Column

        if (storeWarning != null) {
            Text(
                text = storeWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("server_url_headers_warning"),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        CustomHeadersEditor(
            headers = headers,
            onNameChange = onNameChange,
            onValueChange = onValueChange,
            onAdd = onAdd,
            onRemove = onRemove,
            errorIndex = headerError?.index,
            errorReason = headerError?.reason?.toRowError(),
            enabled = enabled,
        )
    }
}

/**
 * `:core:ui` deliberately does not depend on `:core:network`, so the wire-level rejection is mapped to
 * the editor's own enum here. Exhaustive `when` — a new [HeaderRejection] case breaks the build rather
 * than silently rendering no message.
 */
private fun HeaderRejection.toRowError(): CustomHeaderRowError = when (this) {
    HeaderRejection.InvalidName -> CustomHeaderRowError.InvalidName
    HeaderRejection.InvalidValue -> CustomHeaderRowError.InvalidValue
    HeaderRejection.ReservedName -> CustomHeaderRowError.ReservedName
}

@Composable
private fun HttpWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.insecure_connection_title)) },
        text = {
            Text(stringResource(Res.string.insecure_connection_message))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .testTagsAsResourceIdSubtree()
                    .testTag("server_url_http_confirm"),
            ) {
                Text(
                    text = stringResource(Res.string.connect_anyway),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
