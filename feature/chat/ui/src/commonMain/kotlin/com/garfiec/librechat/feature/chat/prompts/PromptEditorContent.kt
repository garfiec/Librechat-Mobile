package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.VerbatimKeyboardOptions
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorContent(
    uiState: PromptEditorUiState,
    commandState: TextFieldState,
    actions: PromptEditorActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            actions.dismissError()
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            actions.consumeSaved()
            currentOnBack()
        }
    }
    LaunchedEffect(uiState.exitRequested) {
        if (uiState.exitRequested) currentOnBack()
    }

    // Reads the command field's text, so typing a command recomposes this too.
    val hasUnsavedChanges = uiState.hasUnsavedChanges(commandState.text.toString())
    // Only while dirty or saving, so a clean form keeps the predictive-back preview.
    PlatformBackHandler(enabled = hasUnsavedChanges || uiState.isSaving, onBack = actions::onBackRequested)
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(
            onDiscard = actions::discardChanges,
            onDismiss = actions::dismissDiscardConfirmation,
        )
    }

    if (uiState.showVersionsSheet && uiState.prompts.isNotEmpty()) {
        PromptVersionsSheet(
            prompts = uiState.prompts,
            productionId = uiState.productionId,
            onDismiss = actions::hideVersionsSheet,
            onSetProduction = actions::setProductionTag,
        )
    }

    AdaptiveScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PromptEditorTopBar(
                isNewPrompt = uiState.isNewPrompt,
                showVersions = !uiState.isNewPrompt && uiState.prompts.isNotEmpty(),
                onBack = actions::onBackRequested,
                onShowVersions = actions::showVersionsSheet,
            )
        },
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@AdaptiveScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AdaptiveOutlinedTextField(
                value = uiState.name,
                onValueChange = actions::updateName,
                label = { Text(stringResource(Res.string.prompt_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdaptiveOutlinedTextField(
                value = uiState.oneliner,
                onValueChange = actions::updateOneliner,
                label = { Text(stringResource(Res.string.prompt_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdaptiveOutlinedTextField(
                state = commandState,
                inputTransformation = CommandInputTransformation,
                label = { Text(stringResource(Res.string.prompt_command_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = VerbatimKeyboardOptions,
                prefix = { Text("/") },
                placeholder = { Text(stringResource(Res.string.prompt_command_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.prompt_content),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))

            AdaptiveOutlinedTextField(
                value = uiState.promptText,
                onValueChange = actions::updatePromptText,
                label = { Text(stringResource(Res.string.prompt_text_label)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                minLines = 5,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(Res.string.prompt_variables_hint))
                },
            )

            if (uiState.promptText.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                PromptVariablesSection(
                    promptText = uiState.promptText,
                    variableValues = uiState.variableValues,
                    onVariableChange = actions::updateVariable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AdaptiveButton(
                onClick = actions::save,
                enabled = uiState.name.isNotBlank() && uiState.promptText.isNotBlank(),
                loading = uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    uiState.isSaving && uiState.isNewPrompt -> Res.string.prompt_editor_creating
                    uiState.isSaving -> Res.string.prompt_editor_saving
                    uiState.isNewPrompt -> Res.string.prompt_editor_create
                    else -> Res.string.save
                }
                Text(stringResource(label))
            }

            Spacer(modifier = Modifier.height(8.dp))

            AdaptiveOutlinedButton(
                onClick = actions::onBackRequested,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.cancel))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Suppress("ModifierMissing")
@Composable
fun PromptEditorTopBar(
    isNewPrompt: Boolean,
    showVersions: Boolean,
    onBack: () -> Unit,
    onShowVersions: () -> Unit,
) {
    AdaptiveTopBar(
        spec = AdaptiveTopBarSpec(
            navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onBack),
            title = BarTitle(
                stringResource(if (isNewPrompt) Res.string.prompt_editor_create_title else Res.string.prompt_editor_edit_title),
            ),
            actions = if (showVersions) {
                listOf(
                    BarAction.Icon(
                        id = "versions",
                        icon = BarIcons.History,
                        label = stringResource(Res.string.cd_version_history),
                        onClick = onShowVersions,
                    ),
                )
            } else {
                emptyList()
            },
        ),
    )
}
