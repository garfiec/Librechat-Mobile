package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Add/edit MCP server dialog with server type dropdown and auth configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpServerDialog(
    editingServer: McpServer?,
    /**
     * The last save was refused with `OAUTH_SECRET_REENTRY_REQUIRED`, so the client secret must
     * be supplied again before this server can be written.
     */
    oauthSecretReentryRequired: Boolean,
    /** The last save was refused with `API_KEY_REENTRY_REQUIRED`: the API key must be re-typed. */
    apiKeyReentryRequired: Boolean,
    /** Why the last save failed; shown here because the dialog stays open and covers the screen. */
    saveError: String?,
    /** A save from this dialog is in flight; Save stays disabled until it answers. */
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave:
    (name: String, description: String?, url: String, type: McpServerType, apiKey: McpApiKeyConfig?, oauth: McpOAuthConfig?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(editingServer?.title ?: editingServer?.name ?: "") }
    var description by remember { mutableStateOf(editingServer?.description ?: "") }
    var url by remember { mutableStateOf(editingServer?.url ?: "") }
    var selectedType by remember { mutableStateOf(editingServer?.type ?: McpServerType.SSE) }

    // Auth state
    val initialAuthMode = remember {
        when {
            // Upstream's form gives OBO precedence over every other auth; with no OBO choice here,
            // "none" is where an OBO server opens, and a save from it resends the stored `obo`.
            editingServer?.obo != null -> McpAuthMode.NONE
            editingServer?.oauth != null -> McpAuthMode.OAUTH
            editingServer?.apiKey != null -> McpAuthMode.API_KEY
            else -> McpAuthMode.NONE
        }
    }
    var authMode by remember { mutableStateOf(initialAuthMode) }

    // API Key fields
    var apiKeyAuthType by remember {
        mutableStateOf(editingServer?.apiKey?.authorizationType ?: McpAuthorizationType.BEARER)
    }
    var apiKeyCustomHeader by remember {
        mutableStateOf(editingServer?.apiKey?.customHeader ?: "")
    }
    // Never pre-filled: reads do not return the key, and a blank key on an admin edit is what
    // asks the server to keep the stored one. See apiKeyConfigFrom.
    var apiKeyValue by remember { mutableStateOf("") }
    var apiKeySource by remember { mutableStateOf(initialApiKeySource(editingServer)) }

    // OAuth fields
    var oauthClientId by remember { mutableStateOf(editingServer?.oauth?.clientId ?: "") }
    var oauthClientSecret by remember { mutableStateOf(editingServer?.oauth?.clientSecret ?: "") }
    var oauthAuthUrl by remember { mutableStateOf(editingServer?.oauth?.authorizationUrl ?: "") }
    var oauthTokenUrl by remember { mutableStateOf(editingServer?.oauth?.tokenUrl ?: "") }
    var oauthScope by remember { mutableStateOf(editingServer?.oauth?.scope ?: "") }

    val isEditing = editingServer != null

    val apiKeyFieldRequester = remember { BringIntoViewRequester() }
    val secretFieldRequester = remember { BringIntoViewRequester() }

    AlertDialog(
        modifier = modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditing) Res.string.edit_mcp_server else Res.string.add_mcp_server))
        },
        text = {
            // A failed save leaves the form wherever the user scrolled it — usually the bottom, next
            // to the button they tapped — so bring the failure into view. A re-entry refusal is
            // told once, by the red field that has to be filled in, so that field is what is
            // brought into view; any other failure leads the form.
            val scrollState = rememberScrollState()
            LaunchedEffect(saveError, apiKeyReentryRequired, oauthSecretReentryRequired) {
                when {
                    apiKeyReentryRequired -> apiKeyFieldRequester.bringIntoView()
                    oauthSecretReentryRequired -> secretFieldRequester.bringIntoView()
                    saveError != null -> scrollState.animateScrollTo(0)
                }
            }
            Column(modifier = Modifier.imePadding().verticalScroll(scrollState)) {
                if (saveError != null) {
                    Text(
                        text = saveError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.mcp_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = {
                        Text(
                            stringResource(Res.string.mcp_description_label) + " " +
                                "(" + stringResource(Res.string.mcp_description_optional) + ")",
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.mcp_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.mcp_type_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val typeOptions = listOf(McpServerType.SSE, McpServerType.STREAMABLE_HTTP)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                ) {
                    typeOptions.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = typeOptions.size,
                            ),
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            Text(
                                when (type) {
                                    McpServerType.SSE -> stringResource(Res.string.mcp_type_sse)
                                    McpServerType.STREAMABLE_HTTP, McpServerType.HTTP -> stringResource(Res.string.mcp_type_streamable_http)
                                    else -> type.name
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.mcp_auth_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    McpAuthMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = authMode == mode,
                            onClick = { authMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = McpAuthMode.entries.size,
                            ),
                        ) {
                            Text(
                                when (mode) {
                                    McpAuthMode.NONE -> stringResource(Res.string.mcp_auth_none)
                                    McpAuthMode.API_KEY -> stringResource(Res.string.mcp_auth_api_key)
                                    McpAuthMode.OAUTH -> stringResource(Res.string.mcp_auth_oauth)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // API Key fields
                AnimatedVisibility(visible = authMode == McpAuthMode.API_KEY) {
                    Column {
                        // Upstream's "Each user provides their own key": unchecked, the admin's key
                        // below is shared by every user; checked, each user is asked for theirs.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = apiKeySource == McpApiKeySource.USER,
                                    role = Role.Checkbox,
                                    onValueChange = { perUser ->
                                        apiKeySource = if (perUser) McpApiKeySource.USER else McpApiKeySource.ADMIN
                                    },
                                ),
                        ) {
                            Checkbox(checked = apiKeySource == McpApiKeySource.USER, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.mcp_api_key_user_provides),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyAuthTypeSelector(
                            selected = apiKeyAuthType,
                            onSelect = { apiKeyAuthType = it },
                        )
                        AnimatedVisibility(visible = apiKeyAuthType == McpAuthorizationType.CUSTOM) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = apiKeyCustomHeader,
                                    onValueChange = { apiKeyCustomHeader = it },
                                    label = { Text(stringResource(Res.string.mcp_header_name_label)) },
                                    placeholder = { Text(stringResource(Res.string.mcp_header_name_placeholder)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // Only the admin supplies a key here; a per-user server asks each user later.
                        AnimatedVisibility(visible = apiKeySource == McpApiKeySource.ADMIN) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = apiKeyValue,
                                    onValueChange = { apiKeyValue = it },
                                    label = { Text(stringResource(Res.string.mcp_api_key_label)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    isError = apiKeyReentryRequired,
                                    supportingText = if (apiKeyReentryRequired) {
                                        { Text(stringResource(Res.string.mcp_api_key_reentry_required)) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(apiKeyFieldRequester),
                                )
                            }
                        }
                    }
                }

                // OAuth fields
                AnimatedVisibility(visible = authMode == McpAuthMode.OAUTH) {
                    Column {
                        OutlinedTextField(
                            value = oauthClientId,
                            onValueChange = { oauthClientId = it },
                            label = { Text(stringResource(Res.string.mcp_oauth_client_id)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = oauthClientSecret,
                            onValueChange = { oauthClientSecret = it },
                            label = { Text(stringResource(Res.string.mcp_oauth_client_secret)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            // The server refused the save because the stored secret was bound to
                            // the endpoints it was issued for and one of them changed. Mark THIS
                            // field, not the dialog: the endpoint edit is what the user wants and
                            // is correct; the secret is the one thing that has to be supplied
                            // again, and it is masked, so nothing about it looks wrong.
                            isError = oauthSecretReentryRequired,
                            supportingText = if (oauthSecretReentryRequired) {
                                { Text(stringResource(Res.string.mcp_oauth_secret_reentry_required)) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(secretFieldRequester),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = oauthAuthUrl,
                            onValueChange = { oauthAuthUrl = it },
                            label = { Text(stringResource(Res.string.mcp_oauth_auth_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = oauthTokenUrl,
                            onValueChange = { oauthTokenUrl = it },
                            label = { Text(stringResource(Res.string.mcp_oauth_token_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = oauthScope,
                            onValueChange = { oauthScope = it },
                            label = { Text(stringResource(Res.string.mcp_oauth_scope)) },
                            placeholder = { Text(stringResource(Res.string.mcp_oauth_scope_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val apiKey = if (authMode == McpAuthMode.API_KEY) {
                        apiKeyConfigFrom(apiKeySource, apiKeyAuthType, apiKeyValue, apiKeyCustomHeader)
                    } else {
                        null
                    }
                    val oauth = if (authMode == McpAuthMode.OAUTH) {
                        McpOAuthConfig(
                            clientId = oauthClientId.trim().ifBlank { null },
                            clientSecret = oauthClientSecret.trim().ifBlank { null },
                            authorizationUrl = oauthAuthUrl.trim().ifBlank { null },
                            tokenUrl = oauthTokenUrl.trim().ifBlank { null },
                            scope = oauthScope.trim().ifBlank { null },
                            // Not editable here, but carried through: the update route replaces
                            // the stored config and treats a dropped method as a changed binding.
                            tokenExchangeMethod = editingServer?.oauth?.tokenExchangeMethod,
                        )
                    } else {
                        null
                    }
                    onSave(name.trim(), description.trim().ifBlank { null }, url.trim(), selectedType, apiKey, oauth)
                },
                enabled = !isSaving && name.isNotBlank() && url.isNotBlank(),
            ) {
                Text(stringResource(if (isEditing) Res.string.action_save else Res.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyAuthTypeSelector(
    selected: McpAuthorizationType,
    onSelect: (McpAuthorizationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = when (selected) {
                McpAuthorizationType.BEARER -> stringResource(Res.string.mcp_auth_bearer)
                McpAuthorizationType.BASIC -> stringResource(Res.string.mcp_auth_basic)
                McpAuthorizationType.CUSTOM -> stringResource(Res.string.mcp_auth_custom)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.mcp_auth_key_type)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            McpAuthorizationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (type) {
                                McpAuthorizationType.BEARER -> stringResource(Res.string.mcp_auth_bearer)
                                McpAuthorizationType.BASIC -> stringResource(Res.string.mcp_auth_basic)
                                McpAuthorizationType.CUSTOM -> stringResource(Res.string.mcp_auth_custom)
                            },
                        )
                    },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
