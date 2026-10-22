package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_collapse
import com.garfiec.librechat.feature.chat.resources.cd_expand
import com.garfiec.librechat.feature.chat.resources.cd_tool_cancelled
import com.garfiec.librechat.feature.chat.resources.cd_tool_failed
import com.garfiec.librechat.feature.chat.resources.skill_call_fallback_name
import com.garfiec.librechat.feature.chat.resources.skill_call_finished
import com.garfiec.librechat.feature.chat.resources.skill_call_running
import com.garfiec.librechat.feature.chat.resources.state_collapsed
import com.garfiec.librechat.feature.chat.resources.state_expanded
import com.garfiec.librechat.feature.chat.resources.tool_call_output
import org.jetbrains.compose.resources.stringResource

/**
 * The tool name of a skill invocation, matched exactly (upstream `Part.tsx`: `toolCall.name ===
 * 'skill'`). Registered in `scripts/mirrors.json` as `skill-tool-call-name`. A manual pick's card
 * and a model-invoked skill both arrive under it.
 */
internal const val SKILL_TOOL_NAME = "skill"

/**
 * Whether a tool call named [name] is a skill invocation: the exact [SKILL_TOOL_NAME], never a
 * tool whose name merely contains it (an MCP tool named `find_skill`) or differs in case.
 */
internal fun isSkillToolCall(name: String?): Boolean = name == SKILL_TOOL_NAME

/**
 * A `skill` tool call (upstream `Parts/SkillCall.tsx`): "Running X" while it runs, "Ran X" once
 * done, "Stopped" when the run cancelled it, with the step's duration, and the skill's output
 * behind a chevron. The model's own one-line intent replaces the label when the call carries one.
 *
 * [skillName] comes from the call's `args.skillName`; a call without one reads as "skill".
 * [output] null or blank leaves the card without a chevron, which is also how the pending rows
 * draw (see [PendingSkillCallRow]).
 */
@Suppress("LongParameterList")
@Composable
internal fun SkillCallCard(
    skillName: String?,
    verdict: ToolCallVerdict,
    modifier: Modifier = Modifier,
    intent: String? = null,
    durationMs: Long? = null,
    output: String? = null,
    // Scopes the saveable expand state: the tool call's id, so it survives the card scrolling away.
    stateKey: String = "",
) {
    val reduceMotion = rememberReduceMotion()
    var isExpanded by rememberSaveable(key = "skillcall:$stateKey") { mutableStateOf(false) }
    val name = skillName ?: stringResource(Res.string.skill_call_fallback_name)
    val title = when (verdict) {
        ToolCallVerdict.RUNNING -> intent ?: stringResource(Res.string.skill_call_running, name)
        ToolCallVerdict.CANCELLED -> stringResource(Res.string.cd_tool_cancelled)
        ToolCallVerdict.COMPLETED, ToolCallVerdict.FAILED -> intent ?: stringResource(Res.string.skill_call_finished, name)
    }
    val localeTag = LocalDateTimeFormat.current.localeTag
    val duration = durationMs?.takeIf { verdict != ToolCallVerdict.RUNNING }?.let { formatTraceDuration(it, localeTag) }
    val expandable = verdict != ToolCallVerdict.RUNNING && !output.isNullOrBlank()
    val toggleLabel = stringResource(if (isExpanded) Res.string.cd_collapse else Res.string.cd_expand)
    val state = stringResource(if (isExpanded) Res.string.state_expanded else Res.string.state_collapsed)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (expandable) {
                        Modifier
                            .clickable(onClickLabel = toggleLabel, role = Role.Button) { isExpanded = !isExpanded }
                            .semantics { stateDescription = state }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (verdict == ToolCallVerdict.RUNNING) {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (duration != null) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (verdict) {
                // The title already reads "Stopped"; a description here would announce it twice.
                ToolCallVerdict.CANCELLED -> Icon(
                    Icons.Default.Block,
                    null,
                    Modifier.padding(start = 8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToolCallVerdict.FAILED -> Icon(
                    Icons.Default.ErrorOutline,
                    stringResource(Res.string.cd_tool_failed),
                    Modifier.padding(start = 8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                ToolCallVerdict.COMPLETED, ToolCallVerdict.RUNNING -> Unit
            }
            if (expandable) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            visible = expandable && isExpanded,
            enter = if (reduceMotion) EnterTransition.None else expandVertically(),
            exit = if (reduceMotion) ExitTransition.None else shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    text = stringResource(Res.string.tool_call_output),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = output.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = OUTPUT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The row a picked skill shows while its turn streams. The server primes manual skills before the
 * run and does not stream their cards: it prepends the completed `skill` parts to the reply's
 * content after the run (`api/server/controllers/agents/client.js`), so they appear only with the
 * final message. Until that FINAL event this "Running X" row stands in; the finalized message's
 * own card replaces it. Web flips its stand-in to "Ran X" once any reply content streams; this
 * row keeps "Running X" until the card itself arrives.
 */
@Composable
internal fun PendingSkillCallRow(
    skillName: String,
    modifier: Modifier = Modifier,
) {
    SkillCallCard(
        skillName = skillName,
        verdict = ToolCallVerdict.RUNNING,
        modifier = modifier,
    )
}

/**
 * The picked skills a streaming reply still owes a card for: the turn's [manualSkills] minus any
 * whose `skill` card the stream has already delivered (a resync after the run can carry the
 * prepended parts). Deduped, in pick order.
 */
internal fun pendingSkillRows(manualSkills: List<String>?, liveSkillNames: Collection<String>): List<String> =
    manualSkills.orEmpty().filter { it.isNotEmpty() && it !in liveSkillNames }.distinct()

private const val OUTPUT_MAX_LINES = 40
