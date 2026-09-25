package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.trace.TraceContent
import com.garfiec.librechat.core.model.trace.TraceErrorCode
import com.garfiec.librechat.core.model.trace.TraceRecord
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import com.garfiec.librechat.core.model.trace.TraceStatus
import com.garfiec.librechat.core.model.trace.TraceSummary
import com.garfiec.librechat.core.model.trace.TraceTurn
import com.garfiec.librechat.core.model.trace.durationMillis
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.trace_back
import com.garfiec.librechat.feature.chat.resources.trace_content_unavailable
import com.garfiec.librechat.feature.chat.resources.trace_empty
import com.garfiec.librechat.feature.chat.resources.trace_error_changed
import com.garfiec.librechat.feature.chat.resources.trace_error_generic
import com.garfiec.librechat.feature.chat.resources.trace_error_not_found
import com.garfiec.librechat.feature.chat.resources.trace_error_rate_limited
import com.garfiec.librechat.feature.chat.resources.trace_error_timeout
import com.garfiec.librechat.feature.chat.resources.trace_error_unauthorized
import com.garfiec.librechat.feature.chat.resources.trace_error_unsupported
import com.garfiec.librechat.feature.chat.resources.trace_input
import com.garfiec.librechat.feature.chat.resources.trace_load_older
import com.garfiec.librechat.feature.chat.resources.trace_metadata
import com.garfiec.librechat.feature.chat.resources.trace_output
import com.garfiec.librechat.feature.chat.resources.trace_partial
import com.garfiec.librechat.feature.chat.resources.trace_refresh
import com.garfiec.librechat.feature.chat.resources.trace_retry
import com.garfiec.librechat.feature.chat.resources.trace_running
import com.garfiec.librechat.feature.chat.resources.trace_stat_cost
import com.garfiec.librechat.feature.chat.resources.trace_stat_errors
import com.garfiec.librechat.feature.chat.resources.trace_stat_generations
import com.garfiec.librechat.feature.chat.resources.trace_stat_tokens
import com.garfiec.librechat.feature.chat.resources.trace_stat_tools
import com.garfiec.librechat.feature.chat.resources.trace_stat_turns
import com.garfiec.librechat.feature.chat.resources.trace_title
import com.garfiec.librechat.feature.chat.resources.trace_tokens_detail
import com.garfiec.librechat.feature.chat.resources.trace_truncated
import com.garfiec.librechat.feature.chat.util.formatAbsoluteTimestamp
import com.garfiec.librechat.feature.chat.viewmodel.TraceViewerViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The conversation trace (v0.8.8-rc3): what the tracing backend recorded for each turn.
 *
 * A deliberately reduced port of upstream's desktop viewer. Its zoomable timeline, collapsible
 * record tree, text filter and Langfuse session link are all absent — they trade screen for
 * navigation, which is the wrong trade on a phone for a surface opened to answer one question.
 * What is kept is what answers it: turns newest first, each record's kind, model, duration and
 * status, and the record detail behind a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TraceViewerSheet(
    conversationId: String,
    isStreaming: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TraceViewerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(conversationId) { viewModel.openFor(conversationId) }

    // A run that SETTLES while the sheet is open added a turn, so the newest page is re-read.
    // The previous value is tracked rather than keying the effect on `isStreaming` alone: that
    // fires on first composition too, which would re-read the page `openFor` has just fetched.
    var wasStreaming by remember { mutableStateOf(isStreaming) }
    LaunchedEffect(isStreaming) {
        val settled = wasStreaming && !isStreaming
        wasStreaming = isStreaming
        if (settled) viewModel.refresh()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp)) {
            val selected = uiState.selectedRecord
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    IconButton(onClick = viewModel::closeDetail) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.trace_back),
                        )
                    }
                }
                Text(
                    text = selected?.name?.ifBlank { null } ?: stringResource(Res.string.trace_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected == null) {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isReading) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.trace_refresh),
                        )
                    }
                }
            }

            if (selected != null) {
                RecordDetail(
                    record = selected,
                    detail = uiState.selectedDetail,
                    isLoading = uiState.isLoadingDetail,
                    errorCode = uiState.detailErrorCode,
                    errorMessage = uiState.detailErrorMessage,
                )
            } else {
                RecordList(
                    turns = uiState.turns,
                    summary = uiState.summary,
                    isLoading = uiState.isLoading,
                    isReading = uiState.isReading,
                    hasOlder = uiState.hasOlder,
                    isEmpty = uiState.isEmpty,
                    errorCode = uiState.errorCode,
                    errorMessage = uiState.errorMessage,
                    onSelect = viewModel::select,
                    onLoadOlder = viewModel::loadOlder,
                    onRetry = viewModel::retry,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecordList(
    turns: List<TraceTurn>,
    summary: TraceSummary,
    isLoading: Boolean,
    isReading: Boolean,
    hasOlder: Boolean,
    isEmpty: Boolean,
    errorCode: String?,
    errorMessage: String?,
    onSelect: (TraceRecord) -> Unit,
    onLoadOlder: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (isLoading) {
            LoadingIndicator(Modifier.fillMaxWidth().height(120.dp))
            return@Column
        }

        if (isEmpty) {
            Text(
                text = stringResource(Res.string.trace_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        if (turns.isNotEmpty()) {
            SummaryRow(summary)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        LazyColumn(Modifier.weight(1f, fill = false)) {
            turns.forEach { turn ->
                item(key = "turn:${turn.messageId}") { TurnHeader(turn) }
                items(turn.rows, key = { it.record.id }) { row ->
                    RecordRow(record = row.record, depth = row.depth, onClick = { onSelect(row.record) })
                }
            }
            if (hasOlder) {
                item(key = "older") {
                    Column {
                        Text(
                            text = stringResource(Res.string.trace_partial, summary.recordCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onLoadOlder, enabled = !isReading) {
                            Text(stringResource(Res.string.trace_load_older))
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            TraceError(errorCode = errorCode, fallback = errorMessage, onRetry = onRetry)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryRow(summary: TraceSummary, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Stat(Res.string.trace_stat_turns, summary.turnCount.toString())
        Stat(Res.string.trace_stat_generations, summary.generationCount.toString())
        Stat(Res.string.trace_stat_tools, summary.toolCallCount.toString())
        if (summary.errorCount > 0) {
            Stat(Res.string.trace_stat_errors, summary.errorCount.toString(), isError = true)
        }
        if (summary.totalTokens > 0) {
            Stat(Res.string.trace_stat_tokens, formatTraceCount(summary.totalTokens))
        }
        // Absent when any generation went unpriced — see TraceSummary.cost.
        summary.cost?.let { Stat(Res.string.trace_stat_cost, formatTraceCost(it)) }
    }
}

@Composable
private fun Stat(
    label: StringResource,
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TurnHeader(turn: TraceTurn, modifier: Modifier = Modifier) {
    val summary = turn.summary
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        // `startTime` is a SORT key — kept as an ISO string so lexicographic order is
        // chronological — not a display value. Shown raw it reads
        // "2026-09-19T14:32:11.523Z", in UTC, directly above bubbles the same screen
        // renders with the formatted stamp this helper produces. An unparseable value
        // formats to "", which falls back to the message id as before.
        val heading = remember(turn.startTime, turn.messageId) {
            turn.startTime.takeIf { it.isNotEmpty() }
                ?.let(::formatAbsoluteTimestamp)
                ?.takeIf { it.isNotEmpty() }
                ?: turn.messageId
        }
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val parts = buildList {
            if (summary.runningCount > 0) add(stringResource(Res.string.trace_running))
            if (summary.totalTokens > 0) add(formatTraceCount(summary.totalTokens))
            summary.cost?.let { add(formatTraceCost(it)) }
        }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordRow(
    record: TraceRecord,
    depth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = remember(record) { record.durationMillis(::parseTraceInstant) }
    val isError = record.status == TraceStatus.ERROR
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * INDENT_DP).dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = record.name.ifBlank { traceKindLabel(record.kind) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    traceKindLabel(record.kind),
                    record.model?.takeIf { it.isNotBlank() },
                    traceStatusLabel(record.status).takeIf { record.status != TraceStatus.OK },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            // Null for a record still running: it has a start and no end, and timing it from "now"
            // would present a number that grows on every re-read as though it were measured.
            text = duration?.let(::formatTraceDuration).orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordDetail(
    record: TraceRecord,
    detail: TraceRecordDetail?,
    isLoading: Boolean,
    errorCode: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = listOfNotNull(
                traceKindLabel(record.kind),
                record.model?.takeIf { it.isNotBlank() },
                traceStatusLabel(record.status),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        record.statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        record.usage?.let { usage ->
            Text(
                text = stringResource(
                    Res.string.trace_tokens_detail,
                    usage.input ?: 0,
                    usage.output ?: 0,
                    usage.total ?: ((usage.input ?: 0) + (usage.output ?: 0)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Rendered whenever present: the server has already filtered it by `interface.contextCost`,
        // so a second gate here would hide a cost the deployment chose to show.
        record.cost?.let {
            Text(
                text = formatTraceCost(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            isLoading -> LoadingIndicator(Modifier.fillMaxWidth().height(96.dp))

            errorMessage != null -> TraceError(errorCode = errorCode, fallback = errorMessage, onRetry = null)

            detail?.contentAvailable == false -> Text(
                text = stringResource(Res.string.trace_content_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            detail != null -> {
                ContentBlock(Res.string.trace_input, detail.input)
                ContentBlock(Res.string.trace_output, detail.output)
                ContentBlock(Res.string.trace_metadata, detail.metadata)
            }
        }
    }
}

@Composable
private fun ContentBlock(
    label: StringResource,
    content: TraceContent?,
    modifier: Modifier = Modifier,
) {
    if (content == null || content.value.isEmpty()) return
    Column(modifier.padding(top = 12.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Horizontally scrollable rather than wrapped: these are JSON payloads, and reflowing them
        // to the sheet's width costs the indentation that makes one readable at all.
        Text(
            text = content.value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        )
        if (content.truncated) {
            Text(
                text = stringResource(Res.string.trace_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The route's own refusals, named.
 *
 * Each says something different about what the user can do — wait out a limiter, retry a timeout,
 * or tell an operator the tracing credentials are rejected — which is the whole reason the server
 * sends a code alongside the sentence.
 */
@Composable
private fun TraceError(
    errorCode: String?,
    fallback: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val message = when (errorCode) {
        TraceErrorCode.NOT_FOUND, TraceErrorCode.DISABLED -> stringResource(Res.string.trace_error_not_found)
        TraceErrorCode.INVALID_REQUEST -> stringResource(Res.string.trace_error_changed)
        TraceErrorCode.RATE_LIMITED -> stringResource(Res.string.trace_error_rate_limited)
        TraceErrorCode.TIMEOUT -> stringResource(Res.string.trace_error_timeout)
        TraceErrorCode.UNAUTHORIZED -> stringResource(Res.string.trace_error_unauthorized)
        TraceErrorCode.UNSUPPORTED -> stringResource(Res.string.trace_error_unsupported)
        else -> fallback.ifBlank { stringResource(Res.string.trace_error_generic) }
    }
    Column(modifier.padding(top = 12.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.trace_retry)) }
        }
    }
}

private const val INDENT_DP = 12
