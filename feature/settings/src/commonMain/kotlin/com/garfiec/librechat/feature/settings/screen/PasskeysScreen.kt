package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.auth.MAX_PASSKEY_NAME_LENGTH
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.IosTextContentType
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.rememberDraftField
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.withIosTextContentType
import com.garfiec.librechat.core.ui.datetime.rememberAbsoluteDate
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.PasskeyRemoveDraft
import com.garfiec.librechat.feature.settings.viewmodel.PasskeyRemoveError
import com.garfiec.librechat.feature.settings.viewmodel.PasskeyRenameDraft
import com.garfiec.librechat.feature.settings.viewmodel.PasskeyRow
import com.garfiec.librechat.feature.settings.viewmodel.PasskeysContent
import com.garfiec.librechat.feature.settings.viewmodel.PasskeysNotice
import com.garfiec.librechat.feature.settings.viewmodel.PasskeysViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › Account › Passkeys: the account's passkeys, with rename and remove. Adding a passkey
 * isn't offered (the server refuses a native app's WebAuthn origin); a line points to the browser.
 */
@Composable
fun PasskeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PasskeysViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-read on every return after the first, so a passkey added or removed on the web shows up.
    OnResume(onReturn = viewModel::refreshOnReturn)

    val alreadyRemoved = stringResource(Res.string.passkeys_already_removed)
    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        when (notice) {
            PasskeysNotice.AlreadyRemoved -> snackbarHostState.showSnackbar(alreadyRemoved)
        }
        viewModel.consumeNotice()
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.passkeys_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "intro", contentType = "intro") {
                    Text(
                        text = stringResource(Res.string.passkeys_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                when (val content = uiState.content) {
                    PasskeysContent.Loading -> adaptiveSection {
                        repeat(PLACEHOLDER_ROWS) { index ->
                            row(key = "placeholder_$index", contentType = "placeholder") { PasskeyPlaceholderRow() }
                        }
                    }
                    PasskeysContent.Failed -> item(key = "failed", contentType = "card") {
                        PasskeysMessageCard(
                            message = stringResource(Res.string.passkeys_load_failed),
                            onRetry = viewModel::load,
                        )
                    }
                    is PasskeysContent.Loaded -> {
                        if (content.rows.isEmpty()) {
                            item(key = "empty", contentType = "card") {
                                PasskeysMessageCard(message = stringResource(Res.string.passkeys_empty), onRetry = null)
                            }
                        } else {
                            adaptiveSection {
                                content.rows.forEach { passkey ->
                                    row(key = passkey.id, contentType = "passkey") {
                                        PasskeyListRow(
                                            passkey = passkey,
                                            onRename = { viewModel.startRename(passkey.id) },
                                            onRemove = { viewModel.startRemove(passkey.id) },
                                        )
                                    }
                                }
                            }
                        }
                        val host = uiState.serverHost
                        val url = uiState.serverUrl
                        if (host != null && url != null) {
                            item(key = "add_in_browser", contentType = "hint") {
                                AddInBrowserLine(host = host, url = url)
                            }
                        }
                    }
                }
                item(key = "bottom", contentType = "spacer") { Spacer(Modifier.height(32.dp)) }
            }
        }

        uiState.rename?.let { draft ->
            RenamePasskeyDialog(
                draft = draft,
                onNameChange = viewModel::onRenameChanged,
                onSave = viewModel::saveRename,
                onDismiss = viewModel::dismissRename,
            )
        }
        uiState.remove?.let { draft ->
            RemovePasskeyDialog(
                draft = draft,
                onPasswordChange = viewModel::onRemovePasswordChanged,
                onConfirm = viewModel::confirmRemove,
                onDismiss = viewModel::dismissRemove,
            )
        }
    }
}

private const val PLACEHOLDER_ROWS = 2

@Composable
private fun PasskeyListRow(
    passkey: PasskeyRow,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by rememberSaveable(passkey.id) { mutableStateOf(false) }
    val added = passkey.createdAt?.let { stringResource(Res.string.passkeys_added_on, rememberAbsoluteDate(it)) }
    val used = passkey.lastUsedAt?.let { stringResource(Res.string.passkeys_last_used_on, rememberAbsoluteDate(it)) }
        ?: stringResource(Res.string.passkeys_never_used)
    val dates = if (added != null) stringResource(Res.string.passkeys_dates, added, used) else used
    val description = if (passkey.synced) {
        stringResource(Res.string.passkeys_row_description_synced, passkey.name, dates)
    } else {
        stringResource(Res.string.passkeys_row_description, passkey.name, dates)
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = adaptiveRowColor) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = passkey.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (passkey.synced) {
                    Spacer(Modifier.width(8.dp))
                    SyncedLabel()
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.passkeys_more_options, passkey.name),
                    )
                }
                AdaptiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.passkeys_action_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.passkeys_action_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
    AdaptiveDivider()
}

@Composable
private fun SyncedLabel() {
    val color = statusColors().success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.passkeys_synced),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun PasskeyPlaceholderRow() {
    Surface(modifier = Modifier.fillMaxWidth().height(56.dp), color = adaptiveRowColor) {}
    AdaptiveDivider()
}

@Composable
private fun PasskeysMessageCard(message: String, onRetry: (() -> Unit)?) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
        }
    }
}

/** "To add a passkey, sign in at <host> in a browser.", with the host as a link to the web app. */
@Composable
private fun AddInBrowserLine(host: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val sentence = stringResource(Res.string.passkeys_add_in_browser, host)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(sentence, host, url, linkColor) {
        val start = sentence.indexOf(host)
        buildAnnotatedString {
            if (start < 0) {
                append(sentence)
                return@buildAnnotatedString
            }
            append(sentence.substring(0, start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = "server",
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ) { runCatching { uriHandler.openUri(url) } },
            ) { append(host) }
            append(sentence.substring(start + host.length))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun RenamePasskeyDialog(
    draft: PasskeyRenameDraft,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Prefilled and selected, so typing replaces the name.
    var field by remember(draft.id) {
        mutableStateOf(TextFieldValue(draft.name, selection = TextRange(0, draft.name.length)))
    }
    LaunchedEffect(draft.id) { focusRequester.requestFocus() }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clearFocusOnTap(),
        title = { Text(stringResource(Res.string.passkeys_rename_title)) },
        text = {
            Column {
                AdaptiveOutlinedTextField(
                    value = field,
                    onValueChange = {
                        val capped = if (it.text.length > MAX_PASSKEY_NAME_LENGTH) {
                            it.copy(text = it.text.take(MAX_PASSKEY_NAME_LENGTH))
                        } else {
                            it
                        }
                        field = capped
                        onNameChange(capped.text)
                    },
                    label = { Text(stringResource(Res.string.passkeys_name_label)) },
                    singleLine = true,
                    enabled = !draft.isSaving,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                if (draft.failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.passkeys_rename_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onSave, enabled = draft.canSave) {
                if (draft.isSaving) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.action_save))
                }
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss, enabled = !draft.isSaving) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RemovePasskeyDialog(
    draft: PasskeyRemoveDraft,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val password = rememberDraftField(draft.password, resetKey = draft.id)
    LaunchedEffect(draft.showPasswordField) {
        if (draft.showPasswordField) focusRequester.requestFocus()
    }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clearFocusOnTap(),
        title = { Text(stringResource(Res.string.passkeys_remove_title, draft.name)) },
        text = {
            Column {
                Text(stringResource(Res.string.passkeys_remove_message))
                if (draft.showPasswordField) {
                    Spacer(Modifier.height(16.dp))
                    // The account password, so a password manager may fill it: not SecretTextField.
                    AdaptiveOutlinedTextField(
                        value = password.value,
                        onValueChange = { password.onValueChange(it, onPasswordChange) },
                        label = { Text(stringResource(Res.string.passkeys_confirm_password)) },
                        singleLine = true,
                        enabled = !draft.isRemoving,
                        isError = draft.error == PasskeyRemoveError.IncorrectPassword,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ).withIosTextContentType(IosTextContentType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentType = ContentType.Password },
                    )
                }
                val error = when (draft.error) {
                    PasskeyRemoveError.IncorrectPassword -> stringResource(Res.string.passkeys_incorrect_password)
                    PasskeyRemoveError.TooManyAttempts -> stringResource(Res.string.passkeys_too_many_attempts)
                    PasskeyRemoveError.Failed -> stringResource(Res.string.passkeys_remove_failed)
                    null -> null
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                onClick = onConfirm,
                enabled = draft.canRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (draft.isRemoving) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.passkeys_action_remove))
                }
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss, enabled = !draft.isRemoving) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
