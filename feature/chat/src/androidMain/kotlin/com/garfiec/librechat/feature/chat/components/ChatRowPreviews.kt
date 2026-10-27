package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.AskUserQuestionRequest
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.model.wakeup.WakeupDisplay
import com.garfiec.librechat.core.model.wakeup.WakeupKind
import com.garfiec.librechat.core.model.wakeup.WakeupTask
import com.garfiec.librechat.core.model.wakeup.WakeupTaskStatus
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The thread's rows, each hosted the way the message list hosts it: a thread turn
// with avatars, or its content column (the row's 8dp gutters, the 28dp avatar and its 6dp gap).

@PreviewNarrowScreens
@Composable
private fun AiTurnMaterialPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { AiTurnHost(siblingCount = 2) }

@PreviewNarrowScreens
@Composable
private fun AiTurnGlassPreview() = RowsPreviewSurface(UiStyle.LIQUID_GLASS) { AiTurnHost(siblingCount = 2) }

@PreviewNarrowScreens
@Composable
private fun WakeupRowPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { WakeupHost() }

@PreviewNarrowScreens
@Composable
private fun AskRecordPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { AskRecordHost() }

@PreviewNarrowScreens
@Composable
private fun AttachmentTilePreview() = RowsPreviewSurface(UiStyle.MATERIAL) { AttachmentTileHost() }

@PreviewNarrowScreens
@Composable
private fun CodeExecutionChipPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { CodeChipHost() }

@PreviewNarrowScreens
@Composable
private fun OfficePreviewFailedPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { OfficeFailedHost() }

@Composable
internal fun RowsPreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        CompositionLocalProvider(
            LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() },
            LocalImmediateMarkdown provides true,
        ) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

/** A thread turn's content column. */
@Composable
internal fun ThreadContent(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 42.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) { content() }
}

/** An AI turn with every action wired, its action row revealed, as the thread lays it out with avatars. */
@Composable
private fun AiTurnHost(siblingCount: Int) {
    MessageBubble(
        message = Message(
            messageId = "preview-ai",
            conversationId = "preview",
            sender = "Assistant",
            text = "Day one: Magic Kingdom at rope drop, then a pool break after lunch.",
        ),
        modifier = Modifier,
        siblingIndex = 0,
        siblingCount = siblingCount,
        onSiblingNavigation = {},
        onEdit = {},
        onRegenerate = {},
        onCopy = {},
        onFeedback = {},
        onReadAloud = {},
        onFork = {},
        baseUrl = "",
        fontSizeMultiplier = 1f,
        isReading = false,
        currentFeedback = null,
        editSession = null,
        userAvatarUrl = null,
        userName = null,
        selectedEndpoint = null,
        showImageDescriptions = false,
        showActionsInitially = true,
        searchQuery = null,
        isSearchMatch = false,
        isCurrentSearchMatch = false,
        searchFocusedOccurrence = -1,
        onFocusedOccurrencePosition = null,
        useKatex = false,
        chatLayoutStyle = ChatLayoutConstants.THREAD,
        showAvatars = true,
        showBubbles = false,
    )
}

/** A settled background tool, with the footer a wake-up keeps from its user bubble. */
@Composable
private fun WakeupHost() {
    SystemEventRow(
        display = WakeupDisplay(
            kind = WakeupKind.BACKGROUND_TOOL,
            tasks = listOf(
                WakeupTask(
                    taskId = "t1",
                    status = WakeupTaskStatus.COMPLETED,
                    result = "Found three flights.",
                    toolName = "search_flights",
                ),
            ),
        ),
        messageId = "preview-wakeup",
        fontSizeMultiplier = 1f,
        useKatex = false,
        siblingCount = 2,
        onSiblingNavigation = {},
        onCopy = {},
        onEdit = {},
        onReadAloud = {},
        onFork = {},
    )
}

/** A short question with a 30-character answer, and no reason or options to expand into. */
@Composable
private fun AskRecordHost() = ThreadContent {
    AskUserQuestionRecordCard(
        question = AskUserQuestionRequest(question = "Which airport should we fly into?"),
        answer = "Orlando International Airport.",
    )
}

@Composable
private fun AttachmentTileHost() {
    AttachmentChipsRow(
        attachedFiles = listOf(
            AttachedFile(uri = "file://report", name = "quarterly-report.pdf", fileId = "f1"),
            AttachedFile(uri = "file://notes", name = "notes.txt", fileId = "f2"),
        ),
        enabledTools = setOf(ToolConstants.SKILLS),
        onRemoveFile = {},
    )
}

@Composable
private fun CodeChipHost() = ThreadContent {
    CodeExecutionCard(
        result = CodeExecutionResult(
            language = "python",
            code = "print(sum(range(10)))",
            output = "45",
            error = null,
            exitCode = 0,
        ),
        phase = CodeExecutionPhase.FINISHED,
    )
}

/** A failed preview of a file with a name long enough to fill the chip's two lines. */
@Composable
private fun OfficeFailedHost() = ThreadContent {
    OfficePreviewCard(
        attachment = Attachment(
            fileId = "office-1",
            filename = "Q3-board-review-final-v2.docx",
            status = FilePreviewResponse.STATUS_FAILED,
            previewError = "the document is password-protected",
        ),
    )
}
