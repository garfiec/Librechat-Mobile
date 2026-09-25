package com.garfiec.librechat.feature.schedules.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.feature.schedules.components.frequencyLabel
import com.garfiec.librechat.feature.schedules.components.problemLabel
import com.garfiec.librechat.feature.schedules.resources.Res
import com.garfiec.librechat.feature.schedules.resources.schedule_agent
import com.garfiec.librechat.feature.schedules.resources.schedule_cadence_preview
import com.garfiec.librechat.feature.schedules.resources.schedule_conflict
import com.garfiec.librechat.feature.schedules.resources.schedule_cron_expression
import com.garfiec.librechat.feature.schedules.resources.schedule_cron_hint
import com.garfiec.librechat.feature.schedules.resources.schedule_days
import com.garfiec.librechat.feature.schedules.resources.schedule_editor_edit_title
import com.garfiec.librechat.feature.schedules.resources.schedule_editor_new_title
import com.garfiec.librechat.feature.schedules.resources.schedule_enable
import com.garfiec.librechat.feature.schedules.resources.schedule_minute
import com.garfiec.librechat.feature.schedules.resources.schedule_name
import com.garfiec.librechat.feature.schedules.resources.schedule_project
import com.garfiec.librechat.feature.schedules.resources.schedule_project_needed
import com.garfiec.librechat.feature.schedules.resources.schedule_project_pinned
import com.garfiec.librechat.feature.schedules.resources.schedule_prompt
import com.garfiec.librechat.feature.schedules.resources.schedule_reload
import com.garfiec.librechat.feature.schedules.resources.schedule_repeats
import com.garfiec.librechat.feature.schedules.resources.schedule_save
import com.garfiec.librechat.feature.schedules.resources.schedule_time
import com.garfiec.librechat.feature.schedules.resources.schedule_timezone
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleDraft
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleEditorUiState
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleEditorViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: String?,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleEditorViewModel = koinViewModel { parametersOf(scheduleId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)

    LaunchedEffect(uiState.savedScheduleId) {
        if (uiState.savedScheduleId != null) currentOnSaveComplete()
    }
    LaunchedEffect(uiState.error) {
        // A conflict keeps its message on screen with a Reload beside it; everything else is a
        // transient report.
        if (!uiState.hasConflict) {
            uiState.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditing) {
                                Res.string.schedule_editor_edit_title
                            } else {
                                Res.string.schedule_editor_new_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = uiState.canSave) {
                        Text(stringResource(Res.string.schedule_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.hasConflict) {
                ConflictNotice(onReload = viewModel::reloadAfterConflict)
            }
            EditorBody(uiState = uiState, onChange = viewModel::update)
            uiState.problem?.let {
                Text(
                    text = problemLabel(it, uiState.limits.minIntervalMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConflictNotice(onReload: () -> Unit) {
    Column {
        Text(
            text = stringResource(Res.string.schedule_conflict),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onReload) { Text(stringResource(Res.string.schedule_reload)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBody(
    uiState: ScheduleEditorUiState,
    onChange: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = uiState.draft
    val enabled = !uiState.hasConflict

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { value -> onChange { it.copy(name = value) } },
        label = { Text(stringResource(Res.string.schedule_name)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.prompt,
        onValueChange = { value -> onChange { it.copy(prompt = value) } },
        label = { Text(stringResource(Res.string.schedule_prompt)) },
        minLines = 3,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )

    LabelledPicker(
        label = stringResource(Res.string.schedule_agent),
        selectedLabel = uiState.agents.firstOrNull { it.id == draft.agentId }?.name.orEmpty(),
        options = uiState.agents.map { it.id to it.name },
        enabled = enabled,
        onSelect = { id -> onChange { it.copy(agentId = id) } },
    )

    Text(stringResource(Res.string.schedule_repeats), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (ScheduleFrequency.STRUCTURED + ScheduleFrequency.CRON).forEach { frequency ->
            FilterChip(
                selected = draft.frequency == frequency,
                onClick = { onChange { it.copy(frequency = frequency) } },
                label = { Text(frequencyLabel(frequency)) },
                enabled = enabled,
            )
        }
    }

    if (draft.isCron) {
        OutlinedTextField(
            value = draft.cronExpression,
            onValueChange = { value -> onChange { it.copy(cronExpression = value) } },
            label = { Text(stringResource(Res.string.schedule_cron_expression)) },
            supportingText = { Text(stringResource(Res.string.schedule_cron_hint)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Raw text, so the field can be cleared and retyped. The draft turns an
            // unparseable value into a problem rather than substituting a default.
            OutlinedTextField(
                value = draft.hourText,
                onValueChange = { value -> onChange { it.copy(hourText = value) } },
                label = { Text(stringResource(Res.string.schedule_time)) },
                singleLine = true,
                enabled = enabled && draft.frequency != ScheduleFrequency.HOURLY,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.minuteText,
                onValueChange = { value -> onChange { it.copy(minuteText = value) } },
                label = { Text(stringResource(Res.string.schedule_minute)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
        if (draft.frequency == ScheduleFrequency.WEEKLY) {
            Text(stringResource(Res.string.schedule_days), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WEEKDAY_LABELS.forEachIndexed { index, label ->
                    FilterChip(
                        selected = index in draft.daysOfWeek,
                        onClick = {
                            onChange {
                                val next = if (index in it.daysOfWeek) {
                                    it.daysOfWeek - index
                                } else {
                                    it.daysOfWeek + index
                                }
                                it.copy(daysOfWeek = next)
                            }
                        },
                        label = { Text(label) },
                        enabled = enabled,
                    )
                }
            }
        }
    }

    Text(
        text = stringResource(Res.string.schedule_cadence_preview, uiState.cadencePreview),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = draft.timezone,
        onValueChange = { value -> onChange { it.copy(timezone = value) } },
        label = { Text(stringResource(Res.string.schedule_timezone)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )

    when {
        // The operator pins the destination and the server ignores any choice sent, so no picker
        // is offered — showing one would imply a decision the user does not have.
        uiState.isProjectPinned -> Text(
            text = stringResource(Res.string.schedule_project_pinned),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.needsAProjectToExist -> Text(
            text = stringResource(Res.string.schedule_project_needed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> LabelledPicker(
            label = stringResource(Res.string.schedule_project),
            selectedLabel = uiState.projects.firstOrNull { it.id == draft.chatProjectId }?.name.orEmpty(),
            options = uiState.projects.map { it.id to it.name },
            enabled = enabled,
            onSelect = { id -> onChange { it.copy(chatProjectId = id) } },
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.schedule_enable), modifier = Modifier.weight(1f))
        Switch(
            checked = draft.enabled,
            onCheckedChange = { value -> onChange { it.copy(enabled = value) } },
            enabled = enabled,
        )
    }
    Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LabelledPicker(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(selectedLabel.ifBlank { "—" }) },
                enabled = enabled && options.isNotEmpty(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

/** Sunday-first, matching cron's own day numbering (0 = Sunday). */
private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
