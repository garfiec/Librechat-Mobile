package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.background.BackgroundTaskDelivery
import com.garfiec.librechat.core.model.background.BackgroundTaskStatus
import com.garfiec.librechat.core.model.background.BackgroundTaskSummary
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.prompts.PromptVersionItem
import com.garfiec.librechat.feature.chat.viewmodel.BackgroundTasksUiState
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadFile
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PickedFile

// Rows whose actions share a line with a label, at the widths and scales where they run out of
// room. Sheets render their content only: a sheet is a window of its own, which a preview cannot
// draw.

@PreviewNarrowScreens
@Composable
private fun BackgroundTasksMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { BackgroundTasksHost() }

@PreviewNarrowScreens
@Composable
private fun BackgroundTasksGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { BackgroundTasksHost() }

@PreviewNarrowScreens
@Composable
private fun UploadRoutingMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { UploadRoutingHost() }

@PreviewNarrowScreens
@Composable
private fun UploadRoutingGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { UploadRoutingHost() }

@PreviewNarrowScreens
@Composable
private fun FeedbackActionsPreview() = PreviewSurface(UiStyle.MATERIAL) { FeedbackActionsHost() }

@PreviewNarrowScreens
@Composable
private fun SetApiKeyHeaderMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { SetApiKeyHeaderHost() }

@PreviewNarrowScreens
@Composable
private fun SetApiKeyHeaderGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { SetApiKeyHeaderHost() }

@PreviewNarrowScreens
@Composable
private fun PromptVersionsPreview() = PreviewSurface(UiStyle.MATERIAL) { PromptVersionsHost() }

@PreviewNarrowScreens
@Composable
private fun InlineEditMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { InlineEditHost() }

@PreviewNarrowScreens
@Composable
private fun InlineEditGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { InlineEditHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

/** Two stoppable tasks, so both Stop all and each row's Stop show. */
@Composable
private fun BackgroundTasksHost() {
    BackgroundTasksContent(
        state = BackgroundTasksUiState(
            conversationId = "preview",
            cancellable = true,
            tasks = listOf(
                BackgroundTaskSummary(taskId = "1", toolName = "web_search", status = BackgroundTaskStatus.RUNNING),
                BackgroundTaskSummary(taskId = "2", toolName = "execute_code", status = BackgroundTaskStatus.RUNNING),
                BackgroundTaskSummary(
                    taskId = "3",
                    toolName = "file_search",
                    status = BackgroundTaskStatus.COMPLETED,
                    delivery = BackgroundTaskDelivery.FAILED,
                ),
            ),
        ),
        onStop = {},
        onStopAll = {},
    )
}

@Composable
private fun UploadRoutingHost() {
    var files by remember {
        mutableStateOf(
            listOf(
                PendingUploadFile(PickedFile(Unit, "quarterly-report.pdf", "application/pdf"), UploadRoute.PROVIDER, true),
                PendingUploadFile(PickedFile(Unit, "notes.docx", "application/msword"), UploadRoute.TEXT, true),
            ),
        )
    }
    UploadRoutingContent(
        files = files,
        onRouteChange = { i, route -> files = files.mapIndexed { j, f -> if (j == i) f.copy(route = route) else f } },
        onApplyToAll = { route -> files = files.map { it.copy(route = route) } },
        onConfirm = {},
        onDismiss = {},
    )
}

@Composable
private fun FeedbackActionsHost() {
    FeedbackActions(submitEnabled = true, onCancel = {}, onSubmit = {}, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SetApiKeyHeaderHost() {
    val dragSelection = rememberListDragSelection()
    Column {
        EndpointGroupHeader(
            endpointName = "anthropic",
            displayLabel = "Anthropic",
            modelCount = 12,
            isExpanded = false,
            iconUrl = null,
            onToggle = {},
            dragSelection = dragSelection,
            needsKey = true,
        )
        EndpointGroupHeader(
            endpointName = "openAI",
            displayLabel = "OpenAI",
            modelCount = 24,
            isExpanded = true,
            iconUrl = null,
            onToggle = {},
            dragSelection = dragSelection,
        )
    }
}

@Composable
private fun PromptVersionsHost() {
    val prompt = Prompt(
        id = "p2",
        groupId = "g",
        author = "me",
        prompt = "Summarize the following text in three bullet points, keeping every number exact.",
        type = "text",
        createdAt = "2026-10-12T09:30:00.000Z",
    )
    Column(Modifier.padding(horizontal = 16.dp)) {
        PromptVersionItem(prompt = prompt, versionNumber = 3, isProduction = true, onSetProduction = {})
        PromptVersionItem(prompt = prompt.copy(id = "p1"), versionNumber = 2, isProduction = false, onSetProduction = {})
    }
}

@Composable
private fun InlineEditHost() {
    var text by remember { mutableStateOf("Can you rewrite this so it reads more formally?") }
    InlineEditInput(
        text = text,
        onTextChange = { text = it },
        onSaveAndSubmit = {},
        onSaveOnly = {},
        onCancel = {},
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
