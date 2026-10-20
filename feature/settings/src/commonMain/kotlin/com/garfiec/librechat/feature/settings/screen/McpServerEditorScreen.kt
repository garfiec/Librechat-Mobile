package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.config.resolveLocalizedConfigText
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpServerType
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.excludeFromAutofill
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.theme.LocalAppLocale
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.McpServerDraft
import com.garfiec.librechat.feature.settings.viewmodel.McpServerEditorUiState
import com.garfiec.librechat.feature.settings.viewmodel.McpServerEditorViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The full-screen add/edit form for one MCP server, grouped as the web's form is. */
@Composable
fun McpServerEditorScreen(
    serverName: String?,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerEditorViewModel = koinViewModel { parametersOf(serverName) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.savedServerName) {
        if (uiState.savedServerName != null) currentOnSaveComplete()
    }
    LaunchedEffect(uiState.exitRequested) {
        if (uiState.exitRequested) currentOnBack()
    }
    // Only while dirty, saving or saved, so a clean form keeps the predictive-back preview. A saved
    // form may be clean (an unchanged Save), and its pop is already on the way.
    PlatformBackHandler(
        enabled = uiState.hasUnsavedChanges || uiState.isSaving || uiState.savedServerName != null,
        onBack = viewModel::onBackRequested,
    )
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(onDiscard = viewModel::discardChanges, onDismiss = viewModel::dismissDiscardConfirmation)
    }

    AdaptiveScaffold(
        // The form ends where the keyboard begins, so a focused field low on it scrolls into view.
        modifier = modifier.imePadding().clearFocusOnTap(),
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    // Through the same check as system back, which never fires on iOS.
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), viewModel::onBackRequested),
                    title = BarTitle(stringResource(if (uiState.isEditing) Res.string.edit_mcp_server else Res.string.add_mcp_server)),
                    actions = listOf(
                        BarAction.Text(
                            id = "save",
                            label = stringResource(if (uiState.isEditing) Res.string.action_save else Res.string.action_add),
                            compactIcon = BarIcons.Confirm,
                            enabled = uiState.canSave,
                            onClick = viewModel::save,
                        ),
                    ),
                ),
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            LoadingIndicator(Modifier.fillMaxSize().padding(padding))
            return@AdaptiveScaffold
        }
        McpServerEditorForm(
            uiState = uiState,
            onChange = viewModel::update,
            onReload = viewModel::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
internal fun McpServerEditorForm(
    uiState: McpServerEditorUiState,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = uiState.draft
    val enabled = !uiState.loadFailed && !uiState.isSaving
    val scrollState = rememberScrollState()
    val apiKeyFieldRequester = remember { BringIntoViewRequester() }
    val secretFieldRequester = remember { BringIntoViewRequester() }

    // A failed save leaves the form wherever the user scrolled it, so bring the failure into view.
    LaunchedEffect(uiState.saveError, uiState.apiKeyReentryRequired, uiState.oauthSecretReentryRequired) {
        when {
            uiState.apiKeyReentryRequired -> apiKeyFieldRequester.bringIntoView()
            uiState.oauthSecretReentryRequired -> secretFieldRequester.bringIntoView()
            uiState.saveError != null -> scrollState.animateScrollTo(0)
        }
    }

    Column(
        modifier = modifier.verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.loadFailed) {
            Column {
                Text(
                    text = stringResource(Res.string.mcp_editor_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onReload) { Text(stringResource(Res.string.action_retry)) }
            }
        }
        uiState.saveError?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        AdaptiveSectionHeader(stringResource(Res.string.mcp_editor_section_about))
        AdaptiveOutlinedTextField(
            value = draft.name,
            onValueChange = { value -> onChange { it.copy(name = value) } },
            label = { Text(stringResource(Res.string.mcp_name_label)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = draft.description,
            onValueChange = { value -> onChange { it.copy(description = value) } },
            label = {
                Text(
                    stringResource(Res.string.mcp_description_label) + " (" +
                        stringResource(Res.string.mcp_description_optional) + ")",
                )
            },
            minLines = 2,
            maxLines = 4,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        AdaptiveSectionHeader(stringResource(Res.string.mcp_editor_section_connection))
        AdaptiveOutlinedTextField(
            value = draft.url,
            onValueChange = { value -> onChange { it.copy(url = value) } },
            label = { Text(stringResource(Res.string.mcp_url_label)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        val typeOptions = listOf(McpServerType.SSE, McpServerType.STREAMABLE_HTTP)
        AdaptiveSegmentedChoice(
            options = listOf(stringResource(Res.string.mcp_type_sse), stringResource(Res.string.mcp_type_streamable_http)),
            // HTTP is the same transport under its older name. stdio and websocket have no segment,
            // so none is selected and the stored transport is kept unless the user picks one.
            selectedIndex = when (draft.type) {
                McpServerType.SSE -> 0
                McpServerType.STREAMABLE_HTTP, McpServerType.HTTP -> 1
                McpServerType.STDIO, McpServerType.WEBSOCKET -> -1
            },
            onSelect = { index -> onChange { it.copy(type = typeOptions[index]) } },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        AdaptiveSectionHeader(stringResource(Res.string.mcp_editor_section_sign_in))
        AdaptiveSegmentedChoice(
            options = McpAuthMode.entries.map { mode ->
                when (mode) {
                    McpAuthMode.NONE -> stringResource(Res.string.mcp_auth_none)
                    McpAuthMode.API_KEY -> stringResource(Res.string.mcp_auth_api_key)
                    McpAuthMode.OAUTH -> stringResource(Res.string.mcp_auth_oauth)
                }
            },
            selectedIndex = draft.authMode.ordinal,
            onSelect = { index -> onChange { it.copy(authMode = McpAuthMode.entries[index]) } },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = draft.authMode == McpAuthMode.API_KEY) {
            ApiKeyFields(uiState, onChange, enabled, apiKeyFieldRequester)
        }
        AnimatedVisibility(visible = draft.authMode == McpAuthMode.OAUTH) {
            OAuthFields(uiState, onChange, enabled, secretFieldRequester)
        }

        if (!uiState.isEditing) {
            TrustRow(uiState, onChange, enabled)
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ApiKeyFields(
    uiState: McpServerEditorUiState,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    enabled: Boolean,
    requester: BringIntoViewRequester,
) {
    val draft = uiState.draft
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Upstream's "Each user provides their own key": unchecked, the admin's key below is shared
        // by every user; checked, each user is asked for theirs.
        CheckboxRow(
            checked = draft.apiKeySource == McpApiKeySource.USER,
            onCheckedChange = { perUser ->
                onChange { it.copy(apiKeySource = if (perUser) McpApiKeySource.USER else McpApiKeySource.ADMIN) }
            },
            enabled = enabled,
        ) {
            Text(stringResource(Res.string.mcp_api_key_user_provides), style = MaterialTheme.typography.bodyMedium)
        }
        ApiKeyAuthTypeSelector(
            selected = draft.apiKeyAuthType,
            onSelect = { type -> onChange { it.copy(apiKeyAuthType = type) } },
            enabled = enabled,
        )
        if (draft.apiKeyAuthType == McpAuthorizationType.CUSTOM) {
            AdaptiveOutlinedTextField(
                value = draft.apiKeyCustomHeader,
                onValueChange = { value -> onChange { it.copy(apiKeyCustomHeader = value) } },
                label = { Text(stringResource(Res.string.mcp_header_name_label)) },
                placeholder = { Text(stringResource(Res.string.mcp_header_name_placeholder)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (draft.apiKeySource == McpApiKeySource.ADMIN) {
            AdaptiveOutlinedTextField(
                value = draft.apiKey,
                onValueChange = { value -> onChange { it.copy(apiKey = value) } },
                label = { Text(stringResource(Res.string.mcp_api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = uiState.apiKeyReentryRequired,
                supportingText = if (uiState.apiKeyReentryRequired) {
                    { Text(stringResource(Res.string.mcp_api_key_reentry_required)) }
                } else {
                    null
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).excludeFromAutofill(),
            )
        }
    }
}

@Composable
private fun OAuthFields(
    uiState: McpServerEditorUiState,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    enabled: Boolean,
    requester: BringIntoViewRequester,
) {
    val draft = uiState.draft
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveOutlinedTextField(
            value = draft.oauthClientId,
            onValueChange = { value -> onChange { it.copy(oauthClientId = value) } },
            label = { Text(stringResource(Res.string.mcp_oauth_client_id)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = draft.oauthClientSecret,
            onValueChange = { value -> onChange { it.copy(oauthClientSecret = value) } },
            label = { Text(stringResource(Res.string.mcp_oauth_client_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            // Flag this field, not the form: the secret is masked, so nothing about it looks wrong.
            isError = uiState.oauthSecretReentryRequired,
            supportingText = if (uiState.oauthSecretReentryRequired) {
                { Text(stringResource(Res.string.mcp_oauth_secret_reentry_required)) }
            } else {
                null
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).excludeFromAutofill(),
        )
        AdaptiveOutlinedTextField(
            value = draft.oauthAuthUrl,
            onValueChange = { value -> onChange { it.copy(oauthAuthUrl = value) } },
            label = { Text(stringResource(Res.string.mcp_oauth_auth_url)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = draft.oauthTokenUrl,
            onValueChange = { value -> onChange { it.copy(oauthTokenUrl = value) } },
            label = { Text(stringResource(Res.string.mcp_oauth_token_url)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = draft.oauthScope,
            onValueChange = { value -> onChange { it.copy(oauthScope = value) } },
            label = { Text(stringResource(Res.string.mcp_oauth_scope)) },
            placeholder = { Text(stringResource(Res.string.mcp_oauth_scope_placeholder)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The acknowledgement the web asks for before a server is added: it will see what the user sends
 * it and act for them. Worded by the deployment when it configures `mcpServers.trustCheckbox`.
 */
@Composable
private fun TrustRow(
    uiState: McpServerEditorUiState,
    onChange: ((McpServerDraft) -> McpServerDraft) -> Unit,
    enabled: Boolean,
) {
    val locale = LocalAppLocale.current
    val label = resolveLocalizedConfigText(uiState.trustCheckbox?.label, locale)
        ?: stringResource(Res.string.mcp_trust_label)
    val subLabel = resolveLocalizedConfigText(uiState.trustCheckbox?.subLabel, locale)
        ?: stringResource(Res.string.mcp_trust_sublabel)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = TrustShape,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        CheckboxRow(
            checked = uiState.draft.trusted,
            onCheckedChange = { trusted -> onChange { it.copy(trusted = trusted) } },
            enabled = enabled,
            modifier = Modifier.padding(12.dp),
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        AdaptiveCheckbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyAuthTypeSelector(
    selected: McpAuthorizationType,
    onSelect: (McpAuthorizationType) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier,
    ) {
        AdaptiveOutlinedTextField(
            value = authTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(Res.string.mcp_auth_key_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            McpAuthorizationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(authTypeLabel(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun authTypeLabel(type: McpAuthorizationType): String = when (type) {
    McpAuthorizationType.BEARER -> stringResource(Res.string.mcp_auth_bearer)
    McpAuthorizationType.BASIC -> stringResource(Res.string.mcp_auth_basic)
    McpAuthorizationType.CUSTOM -> stringResource(Res.string.mcp_auth_custom)
}

private val TrustShape = RoundedCornerShape(12.dp)
