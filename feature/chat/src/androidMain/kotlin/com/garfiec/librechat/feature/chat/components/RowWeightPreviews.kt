package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.background.BackgroundTaskDisplay
import com.garfiec.librechat.core.model.background.PolledTask
import com.garfiec.librechat.core.model.background.PolledTaskStatus
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.model.PresetDisplayData

// Rows whose label and trailing value share one line, each hosted as the thread hosts it with
// avatars on: the bubble's 16dp gutters and the 36dp indent past the avatar.

@PreviewNarrowScreens
@Composable
private fun SenderHeadersPreview() = RowWeightSurface(UiStyle.MATERIAL) { SenderHeadersHost() }

@PreviewNarrowScreens
@Composable
private fun ToolCardsPreview() = RowWeightSurface(UiStyle.MATERIAL) { ToolCardsHost() }

@PreviewNarrowScreens
@Composable
private fun ToolCardsGlassPreview() = RowWeightSurface(UiStyle.LIQUID_GLASS) { ToolCardsHost() }

@PreviewNarrowScreens
@Composable
private fun DisclosureLabelsPreview() = RowWeightSurface(UiStyle.MATERIAL) { DisclosureLabelsHost() }

@PreviewNarrowScreens
@Composable
private fun MermaidHeaderPreview() = RowWeightSurface(UiStyle.MATERIAL) { MermaidHost() }

@PreviewNarrowScreens
@Composable
private fun PresetRowPreview() = RowWeightSurface(UiStyle.MATERIAL) { PresetPickerHost() }

@PreviewNarrowScreens
@Composable
private fun DualPaneModelButtonPreview() = RowWeightSurface(UiStyle.MATERIAL) { ModelButtonHost() }

@Composable
private fun RowWeightSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        CompositionLocalProvider(
            LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() },
            LocalImmediateMarkdown provides true,
        ) {
            // Absolute, so a timestamp reads the same on every render.
            ProvideDateTimeFormat(DateTimeFormatPrefs(style = TimestampStyle.ABSOLUTE)) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
    }
}

/** A thread turn's content column, past the avatar. */
@Composable
private fun RowWeightThreadContent(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) { content() }
}

/** Long sender names over a timestamp, in the thread layout and both sides of the two-sided one. */
@Composable
private fun SenderHeadersHost() {
    Column {
        SenderBubble(previewAiMessage, ChatLayoutConstants.THREAD)
        SenderBubble(previewAiMessage, ChatLayoutConstants.TWO_SIDED)
        SenderBubble(previewUserMessage, ChatLayoutConstants.TWO_SIDED)
        RowWeightThreadContent {
            SteerContentPart(text = "Make it four days.", userName = PREVIEW_USER_NAME, userAvatarUrl = null)
        }
    }
}

@Composable
private fun SenderBubble(message: Message, layout: String) {
    MessageBubble(
        message = message,
        modifier = Modifier,
        siblingIndex = 0,
        siblingCount = 1,
        onSiblingNavigation = {},
        onEdit = {},
        onRegenerate = {},
        onCopy = {},
        onFeedback = {},
        onContinue = null,
        onReadAloud = {},
        onFork = {},
        baseUrl = "",
        fontSizeMultiplier = 1f,
        isReading = false,
        currentFeedback = null,
        editSession = null,
        userAvatarUrl = null,
        userName = PREVIEW_USER_NAME,
        selectedEndpoint = null,
        showImageDescriptions = false,
        showActionsInitially = false,
        searchQuery = null,
        isSearchMatch = false,
        isCurrentSearchMatch = false,
        searchFocusedOccurrence = -1,
        onFocusedOccurrencePosition = null,
        useKatex = false,
        chatLayoutStyle = layout,
        showAvatars = true,
        showBubbles = layout == ChatLayoutConstants.TWO_SIDED,
    )
}

/** Tool cards whose header pairs a title with a status, a percentage or an exit code. */
@Composable
private fun ToolCardsHost() = RowWeightThreadContent {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackgroundTaskCallCard(
            display = BackgroundTaskDisplay.Task(
                PolledTask(taskId = "t1", toolName = "search_flights", status = PolledTaskStatus.CONTROL_NOT_FOUND),
            ),
            output = "",
            intent = "Checked the flight search",
            closedStatus = null,
        )
        MemoryArtifactCard(
            artifact = MemoryArtifact(
                title = "travel_preferences",
                content = null,
                kind = MemoryChangeKind.ERROR,
            ),
        )
        AgentHandoffCard(AgentHandoff(fromAgent = null, toAgent = null, reason = "The planner hands booking off."))
        CodeExecutionCard(
            CodeExecutionResult(language = "python", code = "import missing", output = null, error = "not found", exitCode = 127),
            phase = CodeExecutionPhase.ERROR,
        )
        ImageGenCard(ImageGenResult(isGenerating = true), hideImages = true)
        ToolAttachmentBuckets(
            buckets = listOf(
                ToolAttachmentBucket.Pdfs(
                    listOf(ToolAttachment.Pdf(Attachment(fileId = "pdf-1", filename = "itinerary.pdf"))),
                ),
            ),
            baseUrl = "",
        )
    }
}

/** The pill disclosures that open a list of sources, and the provider-response toggle. */
@Composable
private fun DisclosureLabelsHost() = RowWeightThreadContent {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FileSearchSourcesCard(
            citations = listOf(
                FileSearchCitation(fileId = "f1", fileName = "itinerary.pdf", relevance = 0.82, content = "Day one."),
            ),
        )
        MemoryArtifactsSection(
            artifacts = listOf(MemoryArtifact(title = "travel_preferences", content = "Prefers window seats.")),
        )
        WebSearchCard(
            phase = WebSearchPhase.COMPLETE,
            sources = listOf(
                WebSearchResult(
                    title = "Walt Disney World Resort: park hours and rope drop times",
                    url = "https://disneyworld.disney.go.com/calendars",
                    snippet = "Park hours.",
                ),
            ),
        )
        ErrorContentPart(
            errorText = "The provider rejected the request.",
            detail = "{\n  \"error\": \"rate_limited\",\n  \"retry_after\": 30\n}",
        )
    }
}

@Composable
private fun MermaidHost() = RowWeightThreadContent {
    MermaidDiagram(code = "graph TD\n  A[Rope drop] --> B[Pool break]", modifier = Modifier)
}

/** A preset whose endpoint label is as long as the model beside it. */
@Composable
private fun PresetPickerHost() {
    PresetPicker(
        presets = listOf(
            PresetDisplayData(
                presetId = "p1",
                title = "Trip planner",
                endpointLabel = "Azure OpenAI (EU West)",
                model = "gpt-4o-2024-08-06",
            ),
            PresetDisplayData(presetId = "p2", title = "Quick answers", endpointLabel = "OpenAI", model = "gpt-4o-mini"),
        ),
        onPresetSelect = {},
        onDismiss = {},
    )
}

/** One dual-pane column's model header: half of a 600dp window, holding a 31-character model id. */
@Composable
private fun ModelButtonHost() {
    Box(Modifier.width(280.dp)) {
        ComparisonPaneModelSelector(lane = ComparisonLane.B, label = "anthropic.claude-3-5-sonnet-v2:0", onClick = {})
    }
}

private const val PREVIEW_USER_NAME = "Alexandria Montgomery-Whitfield"

private val previewAiMessage = Message(
    messageId = "preview-ai",
    conversationId = "preview",
    sender = "Claude 3.5 Sonnet (Bedrock US-East)",
    text = "Day one: Magic Kingdom at rope drop.",
    createdAt = "2026-10-18T09:41:00.000Z",
)

private val previewUserMessage = Message(
    messageId = "preview-user",
    conversationId = "preview",
    isCreatedByUser = true,
    text = "Plan four days.",
    createdAt = "2026-10-18T09:40:00.000Z",
)
