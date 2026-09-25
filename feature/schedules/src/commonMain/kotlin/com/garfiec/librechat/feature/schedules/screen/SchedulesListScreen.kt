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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.cadenceToCron
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.feature.schedules.components.disabledReasonLabel
import com.garfiec.librechat.feature.schedules.resources.Res
import com.garfiec.librechat.feature.schedules.resources.schedule_cancel
import com.garfiec.librechat.feature.schedules.resources.schedule_delete
import com.garfiec.librechat.feature.schedules.resources.schedule_delete_confirm_body
import com.garfiec.librechat.feature.schedules.resources.schedule_delete_confirm_title
import com.garfiec.librechat.feature.schedules.resources.schedule_last_run
import com.garfiec.librechat.feature.schedules.resources.schedule_next_run
import com.garfiec.librechat.feature.schedules.resources.schedule_run_now
import com.garfiec.librechat.feature.schedules.resources.schedule_running
import com.garfiec.librechat.feature.schedules.resources.schedules
import com.garfiec.librechat.feature.schedules.resources.schedules_at_limit
import com.garfiec.librechat.feature.schedules.resources.schedules_create
import com.garfiec.librechat.feature.schedules.resources.schedules_disabled_on_server
import com.garfiec.librechat.feature.schedules.resources.schedules_empty_body
import com.garfiec.librechat.feature.schedules.resources.schedules_empty_title
import com.garfiec.librechat.feature.schedules.viewmodel.SchedulesListViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchedulesListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }

    // Nav3 retains this ViewModel, so `init` will not run again after the editor pops. Everything
    // on a card also moves without this client acting, which is why nothing here is cached.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.schedules)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Fail-closed on CREATE, and hidden at the deployment's cap rather than offering a
            // form whose Save is a guaranteed 400.
            if (uiState.canCreate && !uiState.isDisabledOnServer && !uiState.isAtLimit) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.schedules_create))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isDisabledOnServer -> CenteredMessage(
                    stringResource(Res.string.schedules_disabled_on_server),
                )
                uiState.isLoading -> LoadingIndicator()
                uiState.schedules.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.isAtLimit) {
                        item {
                            Text(
                                text = stringResource(Res.string.schedules_at_limit, uiState.limits.maxPerUser),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(uiState.schedules, key = { it.id }) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            canManage = uiState.canCreate,
                            isBusy = uiState.busyScheduleId == schedule.id,
                            onClick = { onEdit(schedule.id) },
                            onToggle = { viewModel.setEnabled(schedule, it) },
                            onRunNow = { viewModel.runNow(schedule, onOpenConversation) },
                            onDelete = { pendingDelete = schedule },
                            onOpenConversation = onOpenConversation,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.schedule_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.schedule_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.delete(schedule)
                }) { Text(stringResource(Res.string.schedule_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.schedule_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.schedules_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.schedules_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    canManage: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    // Gated on the same permission as the Switch, Run now and Delete: every write route requires
    // USE **and** CREATE, and the editor itself carries no permission gate — so a USE-only user
    // who can open it gets a fully armed Save that can only ever 403.
    OutlinedCard(onClick = onClick, enabled = canManage, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schedule.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (canManage) {
                    Switch(checked = schedule.enabled, onCheckedChange = onToggle)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = cadenceToCron(schedule.cadence) + " · " + schedule.timezone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            schedule.nextRunAt?.takeIf { schedule.enabled }?.let {
                Text(
                    text = stringResource(Res.string.schedule_next_run, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            schedule.lastRun?.firedAt?.let {
                Text(
                    text = stringResource(Res.string.schedule_last_run, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A schedule the SERVER paused always says why: several of these name a different
            // repair, and one that stopped with no reason leaves the user nothing to act on.
            //
            // Keyed on the reason, not on `enabled`. `disabledReason` is "why the server turned
            // this off" and is null for a schedule the user paused with the Switch right above —
            // for which the unrecognized-reason fallback ("Paused by the server.") is both wrong
            // and in error red, blaming the server for the user's own tap.
            schedule.disabledReason?.takeIf { !schedule.enabled }?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = disabledReasonLabel(reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // The run rows name the conversation each occurrence is producing, so a chat on its
            // way is reachable before it finishes.
            schedule.inFlight.orEmpty().forEach { run ->
                TextButton(onClick = { onOpenConversation(run.conversationId) }) {
                    Text(stringResource(Res.string.schedule_running))
                }
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRunNow, enabled = !isBusy) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(Res.string.schedule_run_now))
                    }
                    TextButton(onClick = onDelete, enabled = !isBusy) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(Res.string.schedule_delete))
                    }
                }
            }
        }
    }
}
