package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.PtcCallStatus
import com.garfiec.librechat.feature.chat.util.PtcTrace
import com.garfiec.librechat.feature.chat.util.PtcTraceEntry
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Live programmatic tool-calling traces keyed by the code card's tool_call id, supplied by
 * [ChatRoot] from `ChatUiState.ptcTraces`, so the live and the saved code card read the same rows.
 * Empty outside the chat screen (the shared viewer never saw the run). Not static: it changes
 * twice per inner call, and only the code cards that read it should recompose.
 */
val LocalPtcTraces = compositionLocalOf<Map<String, PtcTrace>> { emptyMap() }

/**
 * The tool calls a programmatic tool-calling program makes from inside the sandbox, under the code
 * making them (upstream `PtcToolTrace`): a shell-transcript line per call — status glyph, tool,
 * arguments, duration — with a failure's message under its line, and "+N earlier calls" once the
 * cap has dropped rows. Those inner calls open no run step, so without this the card is one
 * spinner for the whole program. Draws nothing for a call that made none.
 */
@Composable
internal fun PtcToolTrace(trace: PtcTrace, modifier: Modifier = Modifier) {
    if (trace.entries.isEmpty()) return
    val scroll = rememberScrollState()
    // Follows the tail while the program runs, as upstream pins it; a row settling can add an
    // error line, so the key changes on a settle as well as on an append.
    val followKey = "${trace.dropped}:${trace.entries.joinToString("") { it.status.name.take(1) }}"
    LaunchedEffect(followKey) {
        if (!trace.isRunning) return@LaunchedEffect
        // A frame first, so the row just added is laid out and counted in maxValue.
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.ptc_trace_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = TRACE_MAX_HEIGHT)
                .verticalScroll(scroll)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (trace.dropped > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.ptc_trace_earlier, trace.dropped, trace.dropped),
                    style = traceStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trace.entries.forEach { PtcTraceLine(it) }
        }
    }
}

@Composable
private fun PtcTraceLine(entry: PtcTraceEntry) {
    val colors = MaterialTheme.colorScheme
    val failed = entry.status == PtcCallStatus.ERROR
    val parsed = parseToolName(entry.name)
    val toolLabel = parsed.friendlyName?.let { stringResource(it) } ?: parsed.toolName
    val label = parsed.mcpServer?.let { "$it · $toolLabel" } ?: toolLabel
    val statusLabel = stringResource(
        when (entry.status) {
            PtcCallStatus.RUNNING -> Res.string.ptc_trace_running
            PtcCallStatus.SUCCESS -> Res.string.ptc_trace_done
            PtcCallStatus.ERROR -> Res.string.ptc_trace_failed
            PtcCallStatus.INTERRUPTED -> Res.string.ptc_trace_interrupted
        },
    )
    val localeTag = LocalDateTimeFormat.current.localeTag
    // Upstream `isReportableRunStepDuration`: a positive, finite duration; a fast call shows none.
    val duration = entry.durationMs?.takeIf { it > 0 }?.let { formatTraceDuration(it, localeTag) }
    val failure = if (failed) entry.error ?: statusLabel else null
    // The glyph is decorative; a screen reader hears the outcome in words.
    val spoken = listOfNotNull(label, entry.args, statusLabel, duration, failure.takeIf { failed && entry.error != null })
        .joinToString(", ")
    Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        DisableSelection {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = STATUS_GLYPHS.getValue(entry.status),
                    style = traceStyle(),
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(GLYPH_WIDTH),
                )
                Text(
                    text = label,
                    style = traceStyle().copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = entry.args.orEmpty(),
                    style = traceStyle(),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val trailing = duration ?: statusLabel.takeIf { entry.status == PtcCallStatus.RUNNING }
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = traceStyle(),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (failure != null) {
            Text(
                text = failure,
                style = traceStyle(),
                color = colors.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = GLYPH_WIDTH),
            )
        }
    }
}

@Composable
private fun traceStyle() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

/** One glyph per state, so tool names stay aligned as calls settle (upstream `STATUS_GLYPH`). */
private val STATUS_GLYPHS = mapOf(
    PtcCallStatus.RUNNING to "›",
    PtcCallStatus.SUCCESS to "✓",
    PtcCallStatus.ERROR to "✗",
    PtcCallStatus.INTERRUPTED to "?",
)

private val GLYPH_WIDTH = 16.dp
private val TRACE_MAX_HEIGHT = 200.dp
