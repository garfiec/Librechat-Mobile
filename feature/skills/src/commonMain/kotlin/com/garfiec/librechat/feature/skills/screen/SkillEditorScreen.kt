package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SkillEditorScreen(
    skillId: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillEditorViewModel = koinViewModel { parametersOf(skillId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnBack by rememberUpdatedState(onBack)
    val nameField = remember { NameFieldTarget() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SkillEditorEvent.Saved -> currentOnSave(event.skillId)
                SkillEditorEvent.Exit -> currentOnBack()
                // Save is in the top bar and the Name field at the top of a long form: without this
                // a refused save looks like nothing happened.
                SkillEditorEvent.NameTaken -> nameField.reveal()
                // The same for any other refusal: its reason (or the conflict banner) sits at the top.
                SkillEditorEvent.SaveFailed -> scrollState.animateScrollTo(0)
            }
        }
    }

    // Only while dirty or saving, so a clean form keeps the predictive-back preview.
    PlatformBackHandler(enabled = uiState.hasUnsavedChanges || uiState.isSaving, onBack = viewModel::onBackRequested)
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(
            onDiscard = viewModel::discardChanges,
            onDismiss = viewModel::dismissDiscardConfirmation,
        )
    }

    SkillEditorScreenContent(
        state = uiState,
        actions = SkillEditorActions(
            onBack = viewModel::onBackRequested,
            onSave = viewModel::save,
            onNameChange = viewModel::onNameChanged,
            onDisplayTitleChange = viewModel::onDisplayTitleChanged,
            onDescriptionChange = viewModel::onDescriptionChanged,
            onBodyChange = viewModel::onBodyChanged,
            onCategoryChange = viewModel::onCategoryChanged,
            onAlwaysApplyChange = viewModel::onAlwaysApplyChanged,
            onDismissConflict = viewModel::dismissConflictNotice,
            onRetryLoad = viewModel::retryLoad,
        ),
        modifier = modifier,
        nameField = nameField,
        scrollState = scrollState,
    )
}
