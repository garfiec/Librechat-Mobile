package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import com.garfiec.librechat.feature.chat.util.rememberClipboardWriter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A code-execution call's code, language and result, from its args and output. */
data class CodeExecutionResult(
    val language: String?,
    val code: String?,
    val output: String?,
    val error: String?,
    val exitCode: Int?,
    /** The model-authored label from the args, which upstream shows in place of "Analyzing". */
    val intent: String? = null,
    /**
     * The attached-workspace command's own verdict, read from its exit trailer (`parseCommandOutput`)
     * when the server stamped the call `attached_workspace`. When set it alone decides failure:
     * stderr from a command that exited 0 is not an error.
     */
    val command: CommandOutputParts? = null,
)

/**
 * Where a code-execution card is in its life. The live card walks STARTING (only when the server
 * says the sandbox is cold-booting) → RUNNING → a terminal phase; a reloaded call is always
 * terminal. Code and output exist only once the step completes: the wire carries no args at step
 * start and no progressive output.
 */
enum class CodeExecutionPhase { STARTING, RUNNING, FINISHED, ERROR, CANCELLED }

/**
 * The phase for a call whose step resolved to [verdict]. A step that completed still reads as an
 * error when its result says so — a non-zero exit, stderr, or a traceback in stdout (upstream
 * `ERROR_PATTERNS`) — because the run step only reports whether the tool returned.
 */
internal fun codeExecutionPhase(
    verdict: ToolCallVerdict,
    sandboxStarting: Boolean,
    result: CodeExecutionResult?,
): CodeExecutionPhase = when (verdict) {
    ToolCallVerdict.RUNNING -> if (sandboxStarting) CodeExecutionPhase.STARTING else CodeExecutionPhase.RUNNING
    ToolCallVerdict.CANCELLED -> CodeExecutionPhase.CANCELLED
    ToolCallVerdict.FAILED -> CodeExecutionPhase.ERROR
    ToolCallVerdict.COMPLETED -> if (result?.indicatesError() == true) CodeExecutionPhase.ERROR else CodeExecutionPhase.FINISHED
}

internal fun CodeExecutionResult.indicatesError(): Boolean = command?.failed
    ?: (!error.isNullOrBlank() || (exitCode != null && exitCode != 0) || output.looksLikeError())

private fun String?.looksLikeError(): Boolean = this != null && ERROR_PATTERN.containsMatchIn(this)

private val ERROR_PATTERN = Regex("^(Traceback|Error:|Exception:|.*Error:)", RegexOption.MULTILINE)

/**
 * A code-execution call: a status header ("Starting sandbox environment", "Analyzing", "Finished
 * analyzing") with a "language · duration" subtitle, the code behind the header's chevron,
 * and the output capped at [OUTPUT_PREVIEW_LINES] lines. The call's files render outside the card,
 * so collapsing it never hides them.
 */
@Composable
fun CodeExecutionCard(
    result: CodeExecutionResult,
    phase: CodeExecutionPhase,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
    // Keys the expand state: the tool call's id, which the live and the saved card share, so an
    // open card stays open as the reply finishes and when it scrolls away.
    stateKey: String = "",
    // Part of the reply still streaming: hands its state to the saved card when the reply ends.
    live: Boolean = false,
    // Finds this call's programmatic tool-calling trace in [LocalPtcTraces].
    toolCallId: String? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val codeExecCd = stringResource(Res.string.cd_code_execution_result)
    var isCodeExpanded by rememberCarriedExpansion("codeexec", stateKey, live)
    val code = result.code?.takeIf { it.isNotBlank() }
    val settled = phase != CodeExecutionPhase.STARTING && phase != CodeExecutionPhase.RUNNING

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = codeExecCd },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = CARD_SHAPE,
    ) {
        CodeExecutionHeader(
            result = result,
            phase = phase,
            durationMs = durationMs,
            expandable = code != null,
            expanded = isCodeExpanded,
            onToggle = { isCodeExpanded = !isCodeExpanded },
        )
        if (code != null) {
            AnimatedVisibility(
                visible = isCodeExpanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    ToolContentDisclosure(open = isCodeExpanded)
                    CodeBlock(code = code, language = result.language)
                }
            }
        }
        val ptcTrace = toolCallId?.let { LocalPtcTraces.current[it] }
        if (ptcTrace != null) {
            PtcToolTrace(trace = ptcTrace, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
        }
        if (settled && (!result.output.isNullOrBlank() || !result.error.isNullOrBlank())) {
            CodeExecutionOutput(result = result, stateKey = stateKey, live = live)
        }
    }
}

@Composable
private fun CodeExecutionHeader(
    result: CodeExecutionResult,
    phase: CodeExecutionPhase,
    durationMs: Long?,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val localeTag = LocalDateTimeFormat.current.localeTag
    val title = when (phase) {
        CodeExecutionPhase.STARTING -> stringResource(Res.string.sandbox_starting)
        CodeExecutionPhase.RUNNING -> result.intent ?: stringResource(Res.string.analyzing)
        CodeExecutionPhase.CANCELLED -> stringResource(Res.string.cd_tool_cancelled)
        CodeExecutionPhase.FINISHED, CodeExecutionPhase.ERROR -> result.intent
            ?: stringResource(if (result.command != null) Res.string.command_finished else Res.string.analyzing_finished)
    }
    // An attached command that failed names how: "exit code 1", "terminated by SIGKILL", "timed out".
    val verdict = result.command?.takeIf { it.failed }?.let { command ->
        when {
            command.timedOut -> stringResource(Res.string.command_timed_out)
            command.signal != null -> stringResource(Res.string.command_terminated, command.signal)
            command.exitCode != null -> stringResource(Res.string.command_exit_code, command.exitCode)
            else -> null
        }
    }
    val subtitle = listOfNotNull(result.language, verdict, durationMs?.let { formatTraceDuration(it, localeTag) })
        .joinToString(" · ")
    val toggleLabel = stringResource(if (expanded) Res.string.cd_collapse_code else Res.string.cd_expand_code)
    val state = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (expandable) {
                    // The merged title and subtitle stay the label; the action and state are announced.
                    Modifier
                        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                        .semantics { stateDescription = state }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (phase) {
                CodeExecutionPhase.STARTING, CodeExecutionPhase.RUNNING -> AdaptiveCircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
                else -> Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (phase == CodeExecutionPhase.ERROR) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (phase) {
            CodeExecutionPhase.ERROR -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(Res.string.cd_tool_failed),
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
                tint = colors.error,
            )
            CodeExecutionPhase.CANCELLED -> Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
                tint = colors.onSurfaceVariant,
            )
            else -> Unit
        }
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeExecutionOutput(result: CodeExecutionResult, stateKey: String, live: Boolean) {
    val colors = MaterialTheme.colorScheme
    val clipboard = rememberClipboardWriter()
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(COPIED_FEEDBACK_MS)
            showCopied = false
        }
    }
    var showAll by rememberCarriedExpansion("codeexec-out", stateKey, live)
    val stdout = result.output?.takeIf { it.isNotBlank() }
    val stderr = result.error?.takeIf { it.isNotBlank() }
    var stdoutClipped by remember(stdout) { mutableStateOf(false) }
    var stderrClipped by remember(stderr) { mutableStateOf(false) }
    // An attached command's `[exit code: N]…` trailer: shown dimmed after the output, except after a
    // timeout, where it sits behind Details (upstream BashCall) under the "Time limit reached" note.
    val command = result.command
    val trailer = command?.trailer?.trim()?.ifEmpty { null }
    var showTimeoutDetails by rememberCarriedExpansion("codeexec-trailer", stateKey, live)
    val copyText = listOfNotNull(stdout, stderr, trailer).joinToString("\n")
    val outputLabel = stringResource(Res.string.label_output)

    // This pane shows without a disclosure, so Copy on a preview fetches the full output first
    // rather than sitting disabled; a failure reverts the icon and shows the status row.
    val preview = LocalToolPreview.current
    val isPreview = preview?.isPreview == true
    var copyRequested by remember { mutableStateOf(false) }
    var copyPending by remember { mutableStateOf(false) }
    LaunchedEffect(copyPending, isPreview, copyText) {
        if (copyPending && !isPreview) {
            clipboard.copy(copyText, outputLabel)
            showCopied = true
            copyPending = false
        }
    }
    // A retry from a failed fetch still reads the old failure until the new request is seen.
    var staleFailure by remember { mutableStateOf(false) }
    LaunchedEffect(copyPending, preview?.status) {
        when {
            preview?.status != ToolPartStatus.FAILED -> staleFailure = false
            copyPending && !staleFailure -> copyPending = false
        }
    }

    Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = outputLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (isPreview) {
                        staleFailure = preview?.status == ToolPartStatus.FAILED
                        copyRequested = true
                        copyPending = true
                        preview?.key?.let(preview.host::request)
                    } else {
                        clipboard.copy(copyText, outputLabel)
                        showCopied = true
                    }
                },
                enabled = !copyPending,
            ) {
                if (copyPending) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(if (showCopied) Res.string.cd_copied else Res.string.cd_copy_output),
                        modifier = Modifier.size(18.dp),
                        tint = if (showCopied) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
        ToolContentDisclosure(open = showAll || copyRequested, modifier = Modifier.padding(end = 8.dp))
        val maxLines = if (showAll) Int.MAX_VALUE else OUTPUT_PREVIEW_LINES
        Column(modifier = Modifier.padding(end = 8.dp)) {
            if (stdout != null) {
                OutputText(
                    text = stdout,
                    maxLines = maxLines,
                    textColor = if (stdout.looksLikeError()) colors.error else colors.onSurface,
                    background = colors.surfaceContainerLow,
                    onClipChange = { stdoutClipped = it },
                )
            }
            if (stderr != null) {
                if (stdout != null) Spacer(modifier = Modifier.size(4.dp))
                // An attached command's stderr is an error only when the command failed (upstream BashCall).
                val stderrIsError = result.command?.failed ?: true
                OutputText(
                    text = stderr,
                    maxLines = maxLines,
                    textColor = if (stderrIsError) colors.onErrorContainer else colors.onSurfaceVariant,
                    background = if (stderrIsError) colors.errorContainer else colors.surfaceContainerLow,
                    onClipChange = { stderrClipped = it },
                )
            }
            if (trailer != null && (!command.timedOut || showTimeoutDetails)) {
                Text(
                    text = trailer,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (command?.timedOut == true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.command_timeout_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (trailer != null) {
                        val detailsState =
                            stringResource(if (showTimeoutDetails) Res.string.state_expanded else Res.string.state_collapsed)
                        AdaptiveTextButton(
                            onClick = { showTimeoutDetails = !showTimeoutDetails },
                            modifier = Modifier.semantics { stateDescription = detailsState },
                        ) {
                            Text(
                                stringResource(
                                    if (showTimeoutDetails) Res.string.command_timeout_hide else Res.string.command_timeout_details,
                                ),
                            )
                        }
                    }
                }
            }
        }
        if (!showAll && (stdoutClipped || stderrClipped)) {
            AdaptiveTextButton(onClick = { showAll = true }) {
                val count = remember(copyText, isPreview) { outputLineCount(copyText, isPreview) }
                Text(
                    if (count == null) {
                        stringResource(Res.string.show_full_output)
                    } else {
                        pluralStringResource(Res.plurals.show_all_n_lines, count, count)
                    },
                )
            }
        }
    }
}

@Composable
private fun OutputText(
    text: String,
    maxLines: Int,
    textColor: Color,
    background: Color,
    onClipChange: (Boolean) -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = textColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { onClipChange(it.hasVisualOverflow) },
        modifier = Modifier
            .fillMaxWidth()
            .background(background, OUTPUT_SHAPE)
            .padding(8.dp),
    )
}

/**
 * The line count "Show all" may name, or null for a server preview: its cut drops a middle that
 * can hold any number of lines and adds the marker's own, so its count is not the output's.
 */
internal fun outputLineCount(shownText: String, isPreview: Boolean): Int? = if (isPreview) null else shownText.lines().size

private const val OUTPUT_PREVIEW_LINES = 6
private val CARD_SHAPE = RoundedCornerShape(8.dp)
private val OUTPUT_SHAPE = RoundedCornerShape(4.dp)
private const val COPIED_FEEDBACK_MS = 3000L
