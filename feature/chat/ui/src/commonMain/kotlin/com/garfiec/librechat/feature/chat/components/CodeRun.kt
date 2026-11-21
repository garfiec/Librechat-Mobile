package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.model.code.CodeRunLanguages
import com.garfiec.librechat.core.model.code.CodeRunResult
import com.garfiec.librechat.core.model.code.codeRunKey
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.LocalServerUrl
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

/** Where a Run sits in a button's short life (web `RunCode.tsx` `RunState`); no entry is idle. */
enum class CodeRunStatus { RUNNING, COMPLETE, FAILED }

/** Every run the chat knows (stored and made here) and each block's button state, by [codeRunKey]. */
@Immutable
data class CodeRunsState(
    val results: Map<String, List<CodeRunResult>> = emptyMap(),
    val status: Map<String, CodeRunStatus> = emptyMap(),
)

/** One fenced block a Run would send. [key] is where its results are kept. */
@Immutable
data class CodeRunTarget(
    val messageId: String,
    val partIndex: Int?,
    val blockIndex: Int,
    val lang: String,
    val code: String,
) {
    val key: String get() = codeRunKey(messageId, partIndex, blockIndex)
}

/**
 * What the chat lends its replies' code blocks so they can run (web gives `allowExecution` to
 * `MarkdownComponents`' `code` only, behind RUN_CODE.USE). Provided by the chat screen only, so the
 * shared-link viewer, tool cards and the artifact panel never get a Run button.
 */
@Stable
class CodeRunHost(
    val runs: StateFlow<CodeRunsState>,
    val onRun: (CodeRunTarget) -> Unit,
    /** A runnable block is on screen: read the conversation's stored runs, once. */
    val onShown: () -> Unit,
)

val LocalCodeRunHost = staticCompositionLocalOf<CodeRunHost?> { null }

/**
 * The saved reply part being drawn ([partIndex] null for a reply with no content parts), and how
 * many web code blocks earlier chunks of the part held ([baseIndex]), so each fence gets the block
 * index web's per-part counter gives it.
 */
@Immutable
data class CodeRunPart(val messageId: String, val partIndex: Int?, val baseIndex: Int = 0)

val LocalCodeRunPart = compositionLocalOf<CodeRunPart?> { null }

/**
 * Block index bases for a part drawn in [chunks] (artifacts and `\ui` markers split it), whose
 * [texts] are their prose (null for a chunk that isn't): each chunk's is the web code blocks the
 * chunks before it hold. Null outside a runnable part, so other text pays nothing.
 */
@Composable
internal fun rememberCodeRunBases(chunks: Any, texts: () -> List<String?>): IntArray? {
    if (LocalCodeRunPart.current == null) return null
    return remember(chunks) { codeRunBases(texts()) }
}

/** Each chunk's base: the web code blocks in the prose chunks before it (null = not prose). */
internal fun codeRunBases(texts: List<String?>): IntArray {
    var acc = 0
    return IntArray(texts.size) { i -> acc.also { acc += texts[i]?.let(::webCodeBlockCount) ?: 0 } }
}

/** Draws [content] as [part] (null: not runnable, which also clears an outer part). */
@Composable
internal fun ProvideCodeRunPart(part: CodeRunPart?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCodeRunPart provides part, content = content)
}

/** Draws [content] with its chunk's block index base added (see [rememberCodeRunBases]). */
@Composable
internal fun ProvideCodeRunBase(bases: IntArray?, index: Int, content: @Composable () -> Unit) {
    val part = LocalCodeRunPart.current
    val base = bases?.getOrNull(index) ?: 0
    if (part == null || base == 0) {
        content()
    } else {
        CompositionLocalProvider(LocalCodeRunPart provides part.copy(baseIndex = part.baseIndex + base), content = content)
    }
}

/**
 * The run a fenced block offers, or null: no host or part (not a saved reply in the chat), a live
 * reply (the server can't find an unsaved message, and the bubble recomposes per delta), a block web
 * gives no index, or a language the code tool doesn't run.
 */
@Composable
internal fun rememberCodeRunTarget(segment: MarkdownSegment.CodeBlock, streaming: Boolean): CodeRunTarget? {
    if (streaming || LocalCodeRunHost.current == null) return null
    val part = LocalCodeRunPart.current ?: return null
    val index = segment.fenceIndex ?: return null
    return remember(part, index, segment.language, segment.code) {
        CodeRunLanguages.normalize(segment.language)?.let { lang ->
            CodeRunTarget(part.messageId, part.partIndex, part.baseIndex + index, lang, segment.code)
        }
    }
}

/** The Run button in a code block's header: Run, Running, Complete or Failed. */
@Composable
internal fun RunCodeButton(target: CodeRunTarget, host: CodeRunHost) {
    LaunchedEffect(host) { host.onShown() }
    val runs by host.runs.collectAsState()
    val status = runs.status[target.key]
    val running = status == CodeRunStatus.RUNNING
    val tint = when (status) {
        CodeRunStatus.FAILED -> MaterialTheme.colorScheme.error
        CodeRunStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DisableSelection {
        AdaptiveTextButton(
            onClick = { host.onRun(target) },
            enabled = !running,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (running) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = when (status) {
                        CodeRunStatus.COMPLETE -> Icons.Default.Check
                        CodeRunStatus.FAILED -> Icons.Default.Close
                        else -> Icons.Default.Terminal
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(
                    when (status) {
                        CodeRunStatus.RUNNING -> Res.string.run_code_running
                        CodeRunStatus.COMPLETE -> Res.string.run_code_complete
                        CodeRunStatus.FAILED -> Res.string.run_code_failed
                        null -> Res.string.run_code
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * The runs of [target] below its code (web `CodeBlock`'s Output pane): the latest, the run's files,
 * and a switcher through earlier runs. Nothing until a run exists.
 */
@Composable
internal fun CodeRunOutput(target: CodeRunTarget, host: CodeRunHost) {
    val runs by host.runs.collectAsState()
    val results = runs.results[target.key].orEmpty()
    if (results.isEmpty()) return
    // -1 follows the latest, so a new run comes into view as web's does.
    var picked by remember(target.key) { mutableIntStateOf(-1) }
    val shown = if (picked in results.indices) picked else results.lastIndex
    val result = results[shown]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DisableSelection {
                Text(
                    text = stringResource(Res.string.run_code_output),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (results.size > 1) {
                ResultSwitcher(
                    index = shown,
                    count = results.size,
                    onPrevious = { picked = (shown - 1).coerceAtLeast(0) },
                    onNext = { picked = if (shown + 1 >= results.lastIndex) -1 else shown + 1 },
                )
            }
        }
        if (result.output.isNotEmpty()) {
            Text(
                text = result.output,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
            )
        }
        if (result.attachments.isNotEmpty()) {
            val files = remember(result) { result.attachments.map { it.copy(toolCallId = RUN_ATTACHMENTS_ID) } }
            DisableSelection {
                ToolCallAttachments(
                    attachments = files,
                    toolCallId = RUN_ATTACHMENTS_ID,
                    baseUrl = LocalServerUrl.current.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ResultSwitcher(index: Int, count: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    DisableSelection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(Res.string.cd_run_code_previous),
                )
            }
            Text(
                text = stringResource(Res.string.run_code_result_position, index + 1, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onNext, enabled = index < count - 1, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.cd_run_code_next),
                )
            }
        }
    }
}

/** The run's files are its own, whatever call id the server stamped: one bucket. */
private const val RUN_ATTACHMENTS_ID = "code-run"
