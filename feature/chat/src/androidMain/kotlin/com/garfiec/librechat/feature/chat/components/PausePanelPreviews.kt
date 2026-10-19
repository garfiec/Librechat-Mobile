package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AskUserQuestionItem
import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.model.ToolReviewConfig
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// State is hoisted the way PendingActionDelegate hoists it, so interactive mode pages, picks and
// collapses.

@PreviewNarrowScreens
@Composable
private fun AskBatchMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { AskPanelHost(askBatch) }

@PreviewNarrowScreens
@Composable
private fun AskBatchGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { AskPanelHost(askBatch) }

@PreviewNarrowScreens
@Composable
private fun AskBatchCollapsedPreview() =
    PreviewSurface(UiStyle.MATERIAL) { AskPanelHost(askBatch, initiallyCollapsed = true) }

@PreviewNarrowScreens
@Composable
private fun AskSingleQuestionPreview() = PreviewSurface(UiStyle.MATERIAL) { AskPanelHost(askSingle) }

@PreviewNarrowScreens
@Composable
private fun AskStopAfterFailedResumePreview() =
    PreviewSurface(UiStyle.MATERIAL) { AskPanelHost(askSingle, stopShown = true) }

@PreviewNarrowScreens
@Composable
private fun ToolTenCallsPreview() = PreviewSurface(UiStyle.MATERIAL) { ToolPanelHost(toolTen) }

/** The last question of a batch, so the wide row shows Back and Send (n/N) beside Stop. */
@Preview(name = "620dp · 2.0x · de", widthDp = 620, fontScale = 2f, locale = "de", showBackground = true)
@Composable
private fun AskWideActionRowPreview() = PreviewSurface(UiStyle.MATERIAL) {
    AskPanelHost(askBatch, initialActiveId = "dining", stopShown = true)
}

/** 640dp tall with a ~280dp keyboard up, under the floating top bar. */
@Preview(name = "360×640 · keyboard", widthDp = 360, heightDp = 360, showBackground = true)
@Composable
private fun AskDockedUnderKeyboardPreview() = PreviewSurface(UiStyle.MATERIAL) {
    PausePanelDockHost { AskPanelHost(askLongQuestion) }
}

@PreviewNarrowScreens
@Composable
private fun ToolBatchPreview() = PreviewSurface(UiStyle.MATERIAL) { ToolPanelHost(toolBatch) }

@PreviewNarrowScreens
@Composable
private fun ToolBatchAllDecidedPreview() = PreviewSurface(UiStyle.MATERIAL) {
    ToolPanelHost(toolBatch, initialDrafts = toolBatchApproved)
}

/** The last call of a decided batch, so the wide footer shows Back and Continue (n/N). */
@Preview(name = "624dp · 1.5x · ru", widthDp = 624, fontScale = 1.5f, locale = "ru", showBackground = true)
@Composable
private fun ToolWideFooterPreview() = PreviewSurface(UiStyle.MATERIAL) {
    ToolPanelHost(toolBatch, initialDrafts = toolBatchApproved, initialActiveId = "call-2")
}

/** An iPhone SE (375×667) with the keyboard up, an Edit decision open on long arguments. */
@Preview(name = "SE · keyboard", widthDp = 375, heightDp = 400, showBackground = true)
@Composable
private fun ToolEditDockedUnderKeyboardPreview() = PreviewSurface(UiStyle.MATERIAL) {
    PausePanelDockHost(withComposer = true) {
        ToolPanelHost(toolEdit, initialDrafts = toolEditDraft, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

/**
 * The bottom of ChatScreen in a fixed-height window: the docked panel (and the composer, when a
 * tool pause leaves it showing) anchored to the bottom below the floating top bar, which is drawn
 * over the top.
 */
@Composable
private fun PausePanelDockHost(withComposer: Boolean = false, panel: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomCenter).padding(top = PREVIEW_TOP_BAR_HEIGHT)) {
            panel()
            if (withComposer) {
                ChatInput(inputText = "", isStreaming = true, onInputChange = {}, onSend = {}, onStop = {}, onOpenTools = {})
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_TOP_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun AskPanelHost(
    action: PendingAction,
    initiallyCollapsed: Boolean = false,
    initialActiveId: String? = null,
    stopShown: Boolean = false,
) {
    var drafts by remember { mutableStateOf(emptyMap<String, AskAnswerDraft>()) }
    var activeId by remember { mutableStateOf(initialActiveId) }
    var collapsed by remember { mutableStateOf(initiallyCollapsed) }
    AskUserQuestionPanel(
        pendingAction = action,
        isResolving = false,
        drafts = drafts,
        activeQuestionId = activeId,
        collapsed = collapsed,
        onDraftChange = { id, draft -> drafts = drafts + (id to draft) },
        onPickOption = { id, draft, _ -> drafts = drafts + (id to draft) },
        onSelectQuestion = { activeId = it },
        onCollapsedChange = { collapsed = it },
        onCancelAutoAdvance = {},
        onSubmitAnswer = {},
        onSubmitAnswers = {},
        modifier = Modifier.padding(8.dp),
        onStop = {}.takeIf { stopShown },
        resumeFailed = stopShown,
    )
}

@Composable
private fun ToolPanelHost(
    action: PendingAction,
    modifier: Modifier = Modifier,
    initialDrafts: Map<String, ToolDecisionDraft> = emptyMap(),
    initialActiveId: String? = null,
) {
    var drafts by remember { mutableStateOf(initialDrafts) }
    var activeId by remember { mutableStateOf(initialActiveId) }
    var collapsed by remember { mutableStateOf(false) }
    ToolApprovalPanel(
        pendingAction = action,
        isResolving = false,
        drafts = drafts,
        activeCallId = activeId,
        collapsed = collapsed,
        onDraftChange = { id, draft -> drafts = drafts + (id to draft) },
        onPickDecision = { id, draft, _ -> drafts = drafts + (id to draft) },
        onSelectCall = { activeId = it },
        onCollapsedChange = { collapsed = it },
        onCancelAutoAdvance = {},
        onSubmit = {},
        modifier = modifier.padding(8.dp),
    )
}

private fun options(vararg labels: String) = labels.map { AskUserQuestionOption(label = it, value = it) }

/** A short first question beside a four-dot pager, which still squeezes it. */
private val askBatch = PendingAction(
    actionId = "preview-ask",
    payload = PendingActionPayload(
        type = PendingActionTypes.ASK_USER_QUESTION,
        questions = listOf(
            AskUserQuestionItem(
                id = "group",
                question = "Who's in the group of 6?",
                description = "This changes ride picks, pacing, and dining.",
                options = options(
                    "All adults",
                    "Adults and young kids (under 7)",
                    "Adults and older kids/teens",
                    "Multi-generational, including seniors",
                ),
            ),
            AskUserQuestionItem(
                id = "days",
                question = "How many park days do you have, and do you want a rest day in between?",
                options = options("2 days", "3 days", "4+ days"),
            ),
            AskUserQuestionItem(id = "pace", question = "Pace?", options = options("Relaxed", "Packed")),
            AskUserQuestionItem(
                id = "dining",
                question = "Any dining must-haves?",
                options = options("Character dining", "Quick service only"),
                multiSelect = true,
            ),
        ),
    ),
)

private val askSingle = askBatch.copy(
    payload = askBatch.payload?.copy(questions = askBatch.payload?.questions?.take(1)),
)

/** Upstream allows a 2000-character question; this one wraps well past the header's share. */
private val askLongQuestion = askBatch.copy(
    payload = askBatch.payload?.copy(
        questions = listOf(
            AskUserQuestionItem(
                id = "long",
                question = "Before I lock in the itinerary: the group of 6 includes two kids under 7 and a " +
                    "grandparent who can't walk long distances, you have three park days, and one of them " +
                    "overlaps a holiday weekend. Should I plan around shorter days with a midday break, or " +
                    "push for the headline rides early and rest in the evening?",
                description = "This decides the order of parks, where the rest day falls, and which " +
                    "dining reservations are worth booking now.",
                options = options("Shorter days with a break", "Headline rides early", "A mix of both", "Decide later"),
            ),
        ),
    ),
)

private val toolBatch = toolBatchOf(listOf("github_create_pull_request_review_comment", "search_web", "read_file"))

/** Ten calls: the most the pager draws as dots. An ask batch is capped at four questions. */
private val toolTen = toolBatchOf(List(10) { "read_file_${it + 1}" })

private fun toolBatchOf(names: List<String>): PendingAction {
    val requests = names.mapIndexed { i, name ->
        ToolApprovalRequest(
            name = name,
            toolCallId = "call-$i",
            arguments = buildJsonObject { put("query", JsonPrimitive("orlando parks crowd calendar")) },
            description = "Runs $name for the assistant.",
        )
    }
    return PendingAction(
        actionId = "preview-tool",
        payload = PendingActionPayload(
            type = PendingActionTypes.TOOL_APPROVAL,
            actionRequests = requests,
            reviewConfigs = requests.map {
                ToolReviewConfig(
                    actionName = it.name,
                    toolCallId = it.toolCallId,
                    allowedDecisions = listOf(ToolApprovalDecisions.APPROVE, ToolApprovalDecisions.REJECT),
                )
            },
        ),
    )
}

private val toolBatchApproved = toolBatch.payload?.actionRequests.orEmpty()
    .associate { it.toolCallId to ToolDecisionDraft(decision = ToolApprovalDecisions.APPROVE) }

/** One call whose arguments run long, with every decision its policy allows. */
private val toolEdit = run {
    val request = ToolApprovalRequest(
        name = "github_create_pull_request_review_comment",
        toolCallId = "call-edit",
        arguments = buildJsonObject {
            put("owner", JsonPrimitive("garfiec"))
            put("repo", JsonPrimitive("switchboard"))
            put("pull_number", JsonPrimitive(536))
            put(
                "body",
                JsonPrimitive(
                    "This header still wraps on a 320dp phone at 2x. Can the pager drop below the title " +
                        "there, and can the collapse toggle keep its 48dp target while it does?",
                ),
            )
            put("path", JsonPrimitive("feature/chat/src/commonMain/kotlin/components/PausePanelChrome.kt"))
            put("line", JsonPrimitive(142))
        },
        description = "Comments on a line of a pull request's diff.",
    )
    PendingAction(
        actionId = "preview-tool-edit",
        payload = PendingActionPayload(
            type = PendingActionTypes.TOOL_APPROVAL,
            actionRequests = listOf(request),
            reviewConfigs = listOf(
                ToolReviewConfig(
                    actionName = request.name,
                    toolCallId = request.toolCallId,
                    allowedDecisions = listOf(
                        ToolApprovalDecisions.APPROVE,
                        ToolApprovalDecisions.REJECT,
                        ToolApprovalDecisions.EDIT,
                        ToolApprovalDecisions.RESPOND,
                    ),
                ),
            ),
        ),
    )
}

/** Edit picked: the field opens on the call's own arguments, the way a first Edit tap seeds it. */
private val toolEditDraft = toolEdit.payload?.actionRequests.orEmpty().associate {
    it.toolCallId to ToolDecisionDraft(decision = ToolApprovalDecisions.EDIT, editedArguments = it.arguments.toString())
}

private val PREVIEW_TOP_BAR_HEIGHT = 80.dp
