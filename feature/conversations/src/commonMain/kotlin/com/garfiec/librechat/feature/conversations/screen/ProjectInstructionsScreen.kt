package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.project_instructions_about
import com.garfiec.librechat.feature.conversations.resources.project_instructions_about_cd
import com.garfiec.librechat.feature.conversations.resources.project_instructions_close
import com.garfiec.librechat.feature.conversations.resources.project_instructions_conflict
import com.garfiec.librechat.feature.conversations.resources.project_instructions_keep_mine
import com.garfiec.librechat.feature.conversations.resources.project_instructions_label
import com.garfiec.librechat.feature.conversations.resources.project_instructions_load_failed
import com.garfiec.librechat.feature.conversations.resources.project_instructions_ok
import com.garfiec.librechat.feature.conversations.resources.project_instructions_retry
import com.garfiec.librechat.feature.conversations.resources.project_instructions_save
import com.garfiec.librechat.feature.conversations.resources.project_instructions_save_failed
import com.garfiec.librechat.feature.conversations.resources.project_instructions_saved_none
import com.garfiec.librechat.feature.conversations.resources.project_instructions_saved_title
import com.garfiec.librechat.feature.conversations.resources.project_instructions_title
import com.garfiec.librechat.feature.conversations.resources.project_instructions_use_theirs
import com.garfiec.librechat.feature.conversations.resources.project_instructions_view_theirs
import com.garfiec.librechat.feature.conversations.resources.project_length_counter
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectInstructionsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectInstructionsUiState
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectInstructionsViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A project's instructions, full screen (v0.8.9-rc1). A route rather than a sheet: the text can run
 * to 16,000 characters by default (200,000 at most) and a sheet fights the keyboard at that length.
 * Close and back ask before dropping a draft, and do nothing while a save runs.
 */
@Composable
fun ProjectInstructionsScreen(
    projectId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectInstructionsViewModel = koinViewModel { parametersOf(projectId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) currentOnClose()
    }
    // Only while there's something to lose or a save to wait for, so a clean editor keeps the
    // predictive-back preview.
    PlatformBackHandler(enabled = state.isDirty || state.isSaving, onBack = viewModel::onBackRequested)
    if (state.discardPrompt) {
        DiscardChangesDialog(onDiscard = viewModel::confirmDiscard, onDismiss = viewModel::dismissDiscard)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProjectInstructionsEvent.Conflict -> {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(Res.string.project_instructions_conflict),
                        actionLabel = getString(Res.string.project_instructions_view_theirs),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.viewTheirs()
                }
                ProjectInstructionsEvent.SaveFailed ->
                    snackbarHostState.showSnackbar(getString(Res.string.project_instructions_save_failed))
            }
        }
    }

    AdaptiveScaffold(
        // The field ends where the keyboard begins, so the caret's line stays in view.
        modifier = modifier.imePadding(),
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(
                        BarIcons.Close,
                        stringResource(Res.string.project_instructions_close),
                        viewModel::onBackRequested,
                    ),
                    title = BarTitle(stringResource(Res.string.project_instructions_title)),
                    actions = listOf(saveAction(state, viewModel::save)),
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadFailed -> ErrorBanner(
                    message = stringResource(Res.string.project_instructions_load_failed),
                    onRetry = viewModel::load,
                    retryLabel = stringResource(Res.string.project_instructions_retry),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AdaptiveCircularProgressIndicator()
                }
                else -> InstructionsEditor(state, onDraftChange = viewModel::onDraftChange)
            }
        }
    }

    if (state.showTheirs) {
        TheirsSheet(
            text = state.theirs.orEmpty(),
            onUseTheirs = viewModel::useTheirs,
            onKeepMine = viewModel::keepMine,
        )
    }
}

@Composable
private fun saveAction(state: ProjectInstructionsUiState, onSave: () -> Unit): BarAction {
    val label = stringResource(Res.string.project_instructions_save)
    return if (state.isSaving) {
        BarAction.Icon(id = "save", icon = BarIcons.Confirm, label = label, busy = true, onClick = {})
    } else {
        BarAction.Text(id = "save", label = label, compactIcon = BarIcons.Confirm, enabled = state.canSave, onClick = onSave)
    }
}

@Composable
private fun InstructionsEditor(state: ProjectInstructionsUiState, onDraftChange: (String) -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.project_instructions_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showAbout = true }) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.project_instructions_about_cd))
            }
        }
        // Names the field for a screen reader; the label above it is not attached to it.
        val fieldLabel = stringResource(Res.string.project_instructions_label)
        AdaptiveOutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            readOnly = state.isSaving,
            modifier = Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = fieldLabel },
        )
        val localeTag = LocalDateTimeFormat.current.localeTag
        val used = remember(localeTag, state.draft.length) {
            formatLocalizedNumber(state.draft.length.toDouble(), localeTag)
        }
        val max = remember(localeTag, state.maxLength) { formatLocalizedNumber(state.maxLength.toDouble(), localeTag) }
        Text(
            text = stringResource(Res.string.project_length_counter, used, max),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (showAbout) {
        AdaptiveAlertDialog(
            onDismissRequest = { showAbout = false },
            text = { Text(stringResource(Res.string.project_instructions_about)) },
            confirmButton = {
                AdaptiveTextButton(onClick = { showAbout = false }) {
                    Text(stringResource(Res.string.project_instructions_ok))
                }
            },
        )
    }
}

/** The other device's saved text, read-only, after a save conflict. Switchboard-only: web just keeps the draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TheirsSheet(text: String, onUseTheirs: () -> Unit, onKeepMine: () -> Unit) {
    AdaptiveModalBottomSheet(onDismissRequest = onKeepMine) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.project_instructions_saved_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Box(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = text.ifBlank { stringResource(Res.string.project_instructions_saved_none) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                AdaptiveTextButton(onClick = onKeepMine) { Text(stringResource(Res.string.project_instructions_keep_mine)) }
                AdaptiveTextButton(onClick = onUseTheirs) { Text(stringResource(Res.string.project_instructions_use_theirs)) }
            }
        }
    }
}
