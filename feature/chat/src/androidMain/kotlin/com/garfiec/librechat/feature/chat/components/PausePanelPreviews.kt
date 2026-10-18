package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
private fun ToolBatchPreview() = PreviewSurface(UiStyle.MATERIAL) { ToolPanelHost(toolBatch) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun AskPanelHost(action: PendingAction, initiallyCollapsed: Boolean = false) {
    var drafts by remember { mutableStateOf(emptyMap<String, AskAnswerDraft>()) }
    var activeId by remember { mutableStateOf<String?>(null) }
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
    )
}

@Composable
private fun ToolPanelHost(action: PendingAction) {
    var drafts by remember { mutableStateOf(emptyMap<String, ToolDecisionDraft>()) }
    var activeId by remember { mutableStateOf<String?>(null) }
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
        modifier = Modifier.padding(8.dp),
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

private val toolBatch = run {
    val requests = listOf("github_create_pull_request_review_comment", "search_web", "read_file").mapIndexed { i, name ->
        ToolApprovalRequest(
            name = name,
            toolCallId = "call-$i",
            arguments = buildJsonObject { put("query", JsonPrimitive("orlando parks crowd calendar")) },
            description = "Runs $name for the assistant.",
        )
    }
    PendingAction(
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
