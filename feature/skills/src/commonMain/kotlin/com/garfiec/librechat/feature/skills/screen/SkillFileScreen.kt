package com.garfiec.librechat.feature.skills.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One skill file, full screen: text in a monospace editor with a top-bar Save, an image preview, or
 * a note for a file that is neither. Leaving with unsaved edits asks first, through system back and
 * the top bar's back alike (iOS has no system back to catch).
 */
@Composable
fun SkillFileScreen(
    skillId: String,
    relativePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillFileViewModel = koinViewModel { parametersOf(skillId, relativePath) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.saved, uiState.exitRequested) {
        if (uiState.saved || uiState.exitRequested) currentOnBack()
    }
    // Only while there's something to lose or a save to wait for, so a clean file keeps the
    // predictive-back preview. A landed save's pop is already on the way.
    PlatformBackHandler(
        enabled = uiState.isDirty || uiState.isSaving || uiState.saved,
        onBack = viewModel::onBackRequested,
    )
    if (uiState.discardPrompt != null) {
        DiscardChangesDialog(onDiscard = viewModel::confirmDiscard, onDismiss = viewModel::dismissDiscard)
    }

    SkillFileScreenContent(
        state = uiState,
        actions = SkillFileActions(
            onBack = viewModel::onBackRequested,
            onSave = viewModel::save,
            onReload = viewModel::reload,
            onDraftChange = viewModel::onDraftChange,
            onImageFailed = viewModel::onImageFailed,
        ),
        modifier = modifier,
    )
}
