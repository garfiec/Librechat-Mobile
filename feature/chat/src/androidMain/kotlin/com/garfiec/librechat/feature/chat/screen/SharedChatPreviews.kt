package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatError
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatUiState

private val previewMessages = listOf(
    Message(
        messageId = "u1",
        conversationId = "anon",
        parentMessageId = NO_PARENT,
        isCreatedByUser = true,
        text = "Plan a three-day trip to Lisbon in early November for two people who like food and walking.",
    ),
    Message(
        messageId = "a1",
        conversationId = "anon",
        parentMessageId = "u1",
        sender = "Travel planner",
        text = "Day 1: Alfama and the castle, lunch at a tasca, sunset at Miradouro da Senhora do Monte.",
    ),
    Message(
        messageId = "a2",
        conversationId = "anon",
        parentMessageId = "u1",
        sender = "Travel planner",
        text = "Here's a slower plan: one neighbourhood a day, with a long lunch in each.\n\n" +
            "**Day 1 — Alfama.** Morning at the Sé, lunch at a tasca near Largo do Chafariz de Dentro.",
    ),
)

private val loaded = SharedChatUiState(
    snapshot = SharedConversationSnapshot(
        shareId = "6f1c2b9e",
        title = "Three days in Lisbon for two people who like food and walking, in early November",
        createdAt = "2026-10-18T09:00:00.000Z",
        updatedAt = "2026-10-20T10:00:00.000Z",
        messages = previewMessages,
    ),
    displayMessages = buildActiveMessagePath(previewMessages),
    isLoading = false,
)

@PreviewNarrowScreens
@Composable
private fun LoadedMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, loaded)

@PreviewNarrowScreens
@Composable
private fun LoadedGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, loaded)

@PreviewNarrowScreens
@Composable
private fun ContinuingMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, loaded.copy(isContinuing = true))

@PreviewNarrowScreens
@Composable
private fun ContinuingGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, loaded.copy(isContinuing = true))

@PreviewNarrowScreens
@Composable
private fun LoadingMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, SharedChatUiState())

@PreviewNarrowScreens
@Composable
private fun LoadingGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, SharedChatUiState())

internal fun failedSharedChat(error: SharedChatError) = SharedChatUiState(isLoading = false, error = error)

internal const val SHARED_CHAT_PREVIEW_HEIGHT = 720

@Composable
internal fun SharedChatViewerPreview(style: UiStyle, state: SharedChatUiState) {
    LibreChatTheme(darkTheme = style == UiStyle.LIQUID_GLASS, uiStyle = style) {
        Box(Modifier.fillMaxWidth().height(SHARED_CHAT_PREVIEW_HEIGHT.dp)) {
            SharedChatScaffold(
                state = state,
                display = SharedChatDisplay(),
                actions = SharedChatActions(
                    onBack = {},
                    onRetry = {},
                    onContinue = {},
                    onOpenInBrowser = {},
                    onOpenLangfuse = {},
                ),
                snackbarHostState = remember { SnackbarHostState() },
                onSwitchBranch = { _, _ -> },
            )
        }
    }
}
