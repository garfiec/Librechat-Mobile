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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    groupId: String? = null,
    viewModel: PromptEditorViewModel = koinViewModel { parametersOf(groupId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            viewModel.consumeSaved()
            currentOnBack()
        }
    }

    if (uiState.showVersionsSheet && uiState.prompts.isNotEmpty()) {
        PromptVersionsSheet(
            prompts = uiState.prompts,
            productionId = uiState.productionId,
            onDismiss = viewModel::hideVersionsSheet,
            onSetProduction = viewModel::setProductionTag,
        )
    }

    AdaptiveScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onBack),
                    title = BarTitle(if (uiState.isNewPrompt) "Create Prompt" else "Edit Prompt"),
                    actions = if (!uiState.isNewPrompt && uiState.prompts.isNotEmpty()) {
                        listOf(
                            BarAction.Icon(
                                id = "versions",
                                icon = BarIcons.History,
                                label = stringResource(Res.string.cd_version_history),
                                onClick = viewModel::showVersionsSheet,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
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
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(Res.string.prompt_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdaptiveOutlinedTextField(
                value = uiState.oneliner,
                onValueChange = viewModel::updateOneliner,
                label = { Text(stringResource(Res.string.prompt_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdaptiveOutlinedTextField(
                state = viewModel.commandState,
                inputTransformation = CommandInputTransformation,
                label = { Text(stringResource(Res.string.prompt_command_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                prefix = { Text("/") },
                placeholder = { Text("my-command") },
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
                onValueChange = viewModel::updatePromptText,
                label = { Text(stringResource(Res.string.prompt_text_label)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                minLines = 5,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Use {{variable_name}} to add variables")
                },
            )

            if (uiState.promptText.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                PromptVariablesSection(
                    promptText = uiState.promptText,
                    variableValues = uiState.variableValues,
                    onVariableChange = viewModel::updateVariable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AdaptiveButton(
                onClick = viewModel::save,
                enabled = !uiState.isSaving && uiState.name.isNotBlank() && uiState.promptText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    AdaptiveCircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (uiState.isNewPrompt) "Create" else "Save")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AdaptiveOutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.cancel))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
