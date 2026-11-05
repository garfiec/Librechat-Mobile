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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A `read_file`, `create_file` or `edit_file` call (upstream `ReadFileCall` / `FileAuthoringCall`):
 * "Reading x.py" → "Read x.py", "Creating" → "Created" (or "Updated" when the file existed),
 * "Editing" → "Edited", and behind the header's chevron the file as read, the contents written,
 * or the change as a diff — the output's own unified diff when it has one, else the edit's
 * replacements from the arguments. The tool's own summary follows when it says more than that.
 */
@Composable
internal fun FileToolCallCard(
    view: FileToolView,
    verdict: ToolCallVerdict,
    modifier: Modifier = Modifier,
    /** The output reports a failure (`isToolErrorOutput`) although the step completed. */
    outputFailed: Boolean = false,
    durationMs: Long? = null,
    stateKey: String = "",
    live: Boolean = false,
) {
    val reduceMotion = rememberReduceMotion()
    var expanded by rememberCarriedExpansion("filetool", stateKey, live)
    val failed = verdict == ToolCallVerdict.FAILED || (verdict == ToolCallVerdict.COMPLETED && outputFailed)
    val expandable = view.preview.isNotEmpty() || view.diff != null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = CARD_SHAPE,
    ) {
        FileToolHeader(
            view = view,
            verdict = verdict,
            failed = failed,
            durationMs = durationMs,
            expandable = expandable,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
        if (expandable) {
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    ToolContentDisclosure(open = expanded)
                    val diff = view.diff
                    if (diff != null) {
                        DiffView(diff)
                    } else {
                        CodeBlock(
                            code = view.preview,
                            language = view.language.takeIf { view.previewIsCode },
                            wrap = true,
                        )
                    }
                    view.separateOutput?.let { output ->
                        Text(
                            text = output,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileToolHeader(
    view: FileToolView,
    verdict: ToolCallVerdict,
    failed: Boolean,
    durationMs: Long?,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val running = verdict == ToolCallVerdict.RUNNING
    val title = when {
        verdict == ToolCallVerdict.CANCELLED -> stringResource(Res.string.cd_tool_cancelled)
        view.intent != null -> view.intent
        else -> fileToolTitle(view, running)
    }
    val localeTag = LocalDateTimeFormat.current.localeTag
    val diff = view.diff
    val stats = diff?.let { "+${it.additions} −${it.deletions}" }
    val statsCd = diff?.let { stringResource(Res.string.cd_diff_stats, it.additions, it.deletions) }
    val subtitle = listOfNotNull(stats, durationMs?.let { formatTraceDuration(it, localeTag) }).joinToString(" · ")
    val subtitleCd = listOfNotNull(statsCd, durationMs?.let { formatTraceDuration(it, localeTag) }).joinToString(", ")
    val toggleLabel = stringResource(if (expanded) Res.string.cd_collapse else Res.string.cd_expand)
    val state = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (expandable) {
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
            if (running) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
            } else {
                Icon(view.icon(), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
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
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = subtitleCd },
                )
            }
        }
        when {
            failed -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(Res.string.cd_tool_failed),
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
                tint = colors.error,
            )
            verdict == ToolCallVerdict.CANCELLED -> Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
                tint = colors.onSurfaceVariant,
            )
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

/**
 * "Reading x.py" / "Read x.py" and the like. A call that names no file reads as the tool's own
 * name ("Read File"), where upstream would print "Read " with nothing after it.
 */
@Composable
private fun fileToolTitle(view: FileToolView, running: Boolean): String {
    val name = view.fileName ?: return stringResource(
        when (view.kind) {
            FileToolKind.READ -> Res.string.tool_name_read_file
            FileToolKind.CREATE -> Res.string.tool_name_create_file
            FileToolKind.EDIT -> Res.string.tool_name_edit_file
        },
    )
    val resource: StringResource = when (view.kind) {
        FileToolKind.READ -> if (running) Res.string.file_tool_reading else Res.string.file_tool_read
        FileToolKind.CREATE -> when {
            running -> Res.string.file_tool_creating
            view.overwrote -> Res.string.file_tool_updated
            else -> Res.string.file_tool_created
        }
        FileToolKind.EDIT -> if (running) Res.string.file_tool_editing else Res.string.file_tool_edited
    }
    return stringResource(resource, name)
}

private fun FileToolView.icon(): ImageVector = when {
    kind == FileToolKind.READ -> Icons.Default.Description
    kind == FileToolKind.CREATE && !overwrote -> Icons.AutoMirrored.Filled.NoteAdd
    else -> Icons.Default.EditNote
}

/**
 * Upstream `DiffView`: additions and removals on tinted rows with a `+` / `-` gutter, line numbers
 * when the hunk headers carried them, a rule between the edits of an args preview. Wrapped rather
 * than scrolled sideways, and capped at [DIFF_MAX_HEIGHT] with its own scroll.
 */
@Composable
internal fun DiffView(diff: ParsedDiff, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val status = statusColors()
    val addBackground = status.success.copy(alpha = DIFF_TINT_ALPHA)
    val delBackground = status.error.copy(alpha = DIFF_TINT_ALPHA)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHighest, DIFF_SHAPE)
            .heightIn(max = DIFF_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        for (line in diff.visibleLines) {
            when (line.type) {
                DiffLine.Type.HUNK -> if (line.text.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .heightIn(min = 1.dp, max = 1.dp)
                            .background(colors.outlineVariant),
                    )
                } else {
                    DisableSelection {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                else -> DiffRow(
                    line = line,
                    hasLineNumbers = diff.hasLineNumbers,
                    background = when (line.type) {
                        DiffLine.Type.ADD -> addBackground
                        DiffLine.Type.DEL -> delBackground
                        else -> Color.Transparent
                    },
                    markerColor = when (line.type) {
                        DiffLine.Type.ADD -> status.success
                        DiffLine.Type.DEL -> status.error
                        else -> colors.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DiffRow(line: DiffLine, hasLineNumbers: Boolean, background: Color, markerColor: Color) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Row(modifier = Modifier.fillMaxWidth().background(background)) {
        DisableSelection {
            if (hasLineNumbers) {
                Text(
                    text = line.number?.toString().orEmpty(),
                    style = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(DIFF_NUMBER_WIDTH).padding(end = 4.dp),
                )
            }
            Text(
                text = when (line.type) {
                    DiffLine.Type.ADD -> "+"
                    DiffLine.Type.DEL -> "-"
                    else -> ""
                },
                style = mono.copy(fontWeight = FontWeight.SemiBold),
                color = markerColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(DIFF_MARKER_WIDTH),
            )
        }
        Text(
            text = line.text.ifEmpty { " " },
            style = mono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
    }
}

private val CARD_SHAPE = RoundedCornerShape(8.dp)
private val DIFF_SHAPE = RoundedCornerShape(8.dp)
private val DIFF_MAX_HEIGHT = 300.dp
private val DIFF_NUMBER_WIDTH = 36.dp
private val DIFF_MARKER_WIDTH = 24.dp
private const val DIFF_TINT_ALPHA = 0.12f
