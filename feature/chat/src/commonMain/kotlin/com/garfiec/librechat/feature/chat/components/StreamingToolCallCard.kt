package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import org.jetbrains.compose.resources.stringResource

// A dispatcher: each branch emits one card and returns. Wrapping it to satisfy the rule would
// add a layout node to every streaming tool call.
@Suppress("MultipleEmitters")
@Composable
fun StreamingToolCallCard(
    toolCall: ActiveToolCall,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
    streamingAttachments: List<Attachment> = emptyList(),
    showImageDescriptions: Boolean = true,
    /** The server reported this call's sandbox is still cold-booting (`on_sandbox_starting`). */
    sandboxStarting: Boolean = false,
    /** This is the reply's most recent tool call, so a finished search may still be being read. */
    isLatestStep: Boolean = false,
    /** The reply text's length so far, to tell when the model has moved past a finished search. */
    replyLength: Int = 0,
) {
    // Image-gen tool calls render as an ImageGenCard placeholder (matching web): a
    // faux-progress spinner while generating, swapping to the real image the moment
    // its attachment SSE event arrives (linked by toolCallId) — before the final reload.
    if (isImageGenToolCall(toolCall.name.lowercase())) {
        val imageResult = remember(toolCall, streamingAttachments, baseUrl) {
            parseStreamingImageGenResult(toolCall, baseUrl, streamingAttachments)
        }
        ImageGenCard(
            result = imageResult,
            modifier = modifier,
            showDescription = showImageDescriptions,
        )
        return
    }

    // An answered question renders as its Q&A record, so the exchange stays on screen for the
    // rest of the run instead of reappearing only when the message finalizes. The PAUSED one
    // never reaches here — `withoutUnansweredQuestions` drops it while the pause card owns it —
    // but a parallel sibling ask still awaiting its own pause does, and renders as the record
    // card with an empty answer: its question visible, no spinner lying about progress.
    if (isAskUserQuestionToolCall(toolCall.name.lowercase())) {
        // A batched ask reads its questions from `questions[]` and its answers from the output's
        // `{"answers": {…}}` map; neither is visible to the single-question parse below.
        val batch = remember(toolCall.input) { parseAskUserQuestionBatch(toolCall.input) }
        val failed = toolCall.inputValidationError
        if (batch.isNotEmpty()) {
            val answers = remember(toolCall.output, failed) { askUserAnswersOrEmpty(toolCall.output, failed) }
            AskUserQuestionBatchRecordCard(
                questions = batch,
                answers = answers,
                modifier = modifier,
                failed = failed,
            )
            return
        }
        val question = remember(toolCall.input) { parseAskUserQuestion(toolCall.input) }
        AskUserQuestionRecordCard(
            question = question,
            answer = toolCall.output.orEmpty(),
            modifier = modifier,
            failed = failed,
        )
        return
    }

    // A memory write draws the card the reload draws as soon as its output or a memory attachment
    // arrives (web renders MemoryCall live and persisted alike), so a refused write is red at once
    // rather than a generic green check until the message finalizes. Before then, the generic
    // running card: empty output is not a failure.
    val memoryArtifacts = remember(toolCall.name, toolCall.output, toolCall.id, streamingAttachments) {
        streamingMemoryArtifacts(toolCall, streamingAttachments)
    }
    if (memoryArtifacts.isNotEmpty()) {
        val onManage = LocalOpenMemories.current
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            memoryArtifacts.forEachIndexed { index, artifact ->
                MemoryArtifactCard(
                    artifact = artifact,
                    onManage = onManage,
                    stateKey = memoryCardStateKey(toolCall.id, index),
                    live = true,
                )
            }
        }
        return
    }

    // A model-invoked skill streams like any tool call; a manual pick's card only comes with the
    // final message (see PendingSkillCallRow), or with a resync after the run.
    if (isSkillToolCall(toolCall.name)) {
        val skillName = remember(toolCall.input) { parseSkillName(toolCall.input) }
        val intent = remember(toolCall.input) { parseToolIntent(toolCall.input) }
        SkillCallCard(
            skillName = skillName,
            verdict = toolCallVerdict(toolCall.closedStatus, toolCall.isComplete),
            intent = intent,
            durationMs = toolCall.durationMs,
            output = toolCall.output,
            stateKey = toolCall.id,
            modifier = modifier,
        )
        return
    }

    // Status only while the step runs — the wire carries no args at step start and no progressive
    // output — then the code and output the completion reports, in the card the reload renders.
    if (isCodeExecutionToolCall(toolCall.name.lowercase())) {
        val result = remember(toolCall.name, toolCall.input, toolCall.output) {
            parseCodeExecution(toolCall.name, toolCall.input, toolCall.output)
        }
        val verdict = toolCallVerdict(toolCall.closedStatus, toolCall.isComplete)
        Column(modifier = modifier.fillMaxWidth()) {
            CodeExecutionCard(
                result = result ?: EMPTY_CODE_RESULT,
                phase = codeExecutionPhase(verdict, sandboxStarting, result),
                durationMs = toolCall.durationMs,
                stateKey = toolCall.id,
                live = true,
            )
            ToolCallAttachments(
                attachments = streamingAttachments,
                toolCallId = toolCall.id,
                baseUrl = baseUrl,
                modifier = Modifier.padding(top = 8.dp),
                live = true,
            )
        }
        return
    }

    val nameLower = toolCall.name.lowercase()
    val verdict = toolCallVerdict(toolCall.closedStatus, toolCall.isComplete)
    // The reply's length when this call finished: text growing past it means the model has moved on.
    val replyLengthAtComplete =
        // Saveable: this is a LazyColumn item, and a plain remember re-captured on scroll-back would
        // put a settled search back to "Reading results".
        rememberSaveable(toolCall.id, toolCall.isComplete) { if (toolCall.isComplete) replyLength else -1 }

    // Web search: the phase line while it runs ("Searching the web" → "Processing results" →
    // "Reading results"), then the same settled card the finalized message uses, with sources
    // arriving live as each `web_search` attachment re-emits.
    if (isWebSearchToolCall(nameLower)) {
        val searchData =
            remember(streamingAttachments, toolCall.id) { webSearchData(streamingAttachments, toolCall.id) }
        val sources =
            remember(streamingAttachments, toolCall.id) { collectWebSearchSources(streamingAttachments, toolCall.id) }
        val outcome = searchOutcome(verdict, toolCall.output)
        val searchCard = claimsWebSearchCard(nameLower, hasSources = sources.isNotEmpty())
        if (searchCard && outcome == SearchOutcome.HIDDEN) return
        if (searchCard && outcome == SearchOutcome.SHOWN) {
            val phase = liveWebSearchPhase(
                isComplete = verdict != ToolCallVerdict.RUNNING,
                hasSources = sources.isNotEmpty(),
                isLatestStep = isLatestStep,
                replyMovedOn = replyLengthAtComplete >= 0 && replyLength > replyLengthAtComplete,
            )
            WebSearchCard(
                sources = sources,
                phase = phase,
                modifier = modifier,
                turn = webSearchTurn(searchData),
                intent = remember(toolCall.input) { parseToolIntent(toolCall.input) },
                query = remember(toolCall.input) { parseSearchQuery(toolCall.input) },
                answerBox = remember(searchData) { webSearchAnswerBox(searchData) },
                verticals = remember(searchData) { collectSearchVerticals(searchData) },
                stateKey = toolCall.id,
            )
            return
        }
    }

    // File search: "Searching your files" while it runs, then its sources card.
    if (isFileSearchToolCall(nameLower) && searchOutcome(verdict, toolCall.output) != SearchOutcome.FAILED) {
        val citations = remember(streamingAttachments, toolCall.id, toolCall.output) {
            fileSearchResults(streamingAttachments, toolCall.id, toolCall.output)
        }
        // A settled search with nothing to list falls through to the generic card, so its output stays readable.
        if (citations.isNotEmpty() || verdict != ToolCallVerdict.COMPLETED) {
            val fileActions = rememberToolFileActions(baseUrl)
            FileSearchSourcesCard(
                citations = citations,
                modifier = modifier,
                stateKey = toolCall.id,
                verdict = verdict,
                intent = remember(toolCall.input) { parseToolIntent(toolCall.input) },
                durationMs = toolCall.durationMs,
                onOpen = { citation -> citation.asAttachment()?.let(fileActions::openToolFile) },
            )
            return
        }
    }

    GenericStreamingToolCard(
        toolCall = toolCall,
        baseUrl = baseUrl,
        streamingAttachments = streamingAttachments,
        modifier = modifier,
    )
}

/** The default streaming tool-call card: name, progress/complete state, expandable output, and
 *  any files this tool call has generated so far. Used when no specialized card applies. */
@Composable
private fun GenericStreamingToolCard(
    toolCall: ActiveToolCall,
    baseUrl: String,
    streamingAttachments: List<Attachment>,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val canExpand = toolCall.isComplete && !toolCall.output.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canExpand) {
                                Modifier.clickable { isExpanded = !isExpanded }
                            } else {
                                Modifier
                            },
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = toolCall.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    when (toolCallVerdict(toolCall.closedStatus, toolCall.isComplete)) {
                        ToolCallVerdict.CANCELLED -> Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = stringResource(Res.string.cd_tool_cancelled),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        ToolCallVerdict.FAILED -> Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = stringResource(Res.string.cd_tool_failed),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )

                        ToolCallVerdict.COMPLETED -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.cd_tool_complete),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )

                        ToolCallVerdict.RUNNING -> AdaptiveCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (canExpand) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(if (isExpanded) Res.string.cd_collapse else Res.string.cd_expand),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded && canExpand,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.tool_call_output),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = toolCall.output.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Files this tool call has generated so far (e.g. a code-interpreter PDF/PNG), routed by
        // toolCallId. Office-preview docs are rendered separately (streaming_office_previews).
        ToolCallAttachments(
            attachments = streamingAttachments,
            toolCallId = toolCall.id,
            baseUrl = baseUrl,
            modifier = Modifier.padding(top = 8.dp),
            live = true,
        )
    }
}

private val EMPTY_CODE_RESULT =
    CodeExecutionResult(language = null, code = null, output = null, error = null, exitCode = null)
