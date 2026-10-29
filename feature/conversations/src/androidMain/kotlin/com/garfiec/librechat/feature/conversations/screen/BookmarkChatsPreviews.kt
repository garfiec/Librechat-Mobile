package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsUiState

// A bookmark's chats at the widths and translations that squeeze them: a long bookmark name and a
// description long enough to clip, long chat titles; the loading, empty and error states.

private const val LONG_NAME = "Recipes to try when the whole family visits over the long weekend in spring"

private val sampleBookmark = ConversationTag(
    tag = LONG_NAME,
    description = "Mains, sides and desserts that scale to twelve people, plus the shopping lists and the timings " +
        "for a single oven, collected over three years of holidays",
    count = 3,
)

private fun row(id: String, title: String, favorite: Boolean = false) = ConversationDisplayData(
    conversationId = id,
    title = title,
    endpoint = "openAI",
    model = "gpt-4o",
    updatedAt = null,
    isFavorite = favorite,
)

private val withChats = BookmarkChatsUiState(
    name = LONG_NAME,
    bookmark = sampleBookmark,
    groupedConversations = listOf(
        DateGroup.Today to listOf(row("a", "Slow-roast lamb shoulder for twelve with a make-ahead gravy", favorite = true)),
        DateGroup.Previous30Days to listOf(row("b", "Lemon tart"), row("c", "Oven timings for the whole menu")),
    ),
    conversationCount = 3,
    isLoading = false,
)

@Composable
private fun Host(style: UiStyle, state: BookmarkChatsUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.height(560.dp).background(MaterialTheme.colorScheme.surface)) {
            BookmarkChatsList(
                state = state,
                listState = rememberLazyListState(),
                onRetry = {},
                onConversationClick = {},
                onActionsClick = {},
            )
        }
    }
}

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsMaterialPreview() = Host(UiStyle.MATERIAL, withChats)

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsGlassPreview() = Host(UiStyle.LIQUID_GLASS, withChats)

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsEmptyMaterialPreview() =
    Host(UiStyle.MATERIAL, BookmarkChatsUiState(name = "Taxes", bookmark = ConversationTag(tag = "Taxes"), isLoading = false))

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsEmptyGlassPreview() =
    Host(UiStyle.LIQUID_GLASS, BookmarkChatsUiState(name = "Taxes", bookmark = ConversationTag(tag = "Taxes"), isLoading = false))

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsLoadingMaterialPreview() = Host(UiStyle.MATERIAL, BookmarkChatsUiState(name = LONG_NAME))

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsLoadingGlassPreview() = Host(UiStyle.LIQUID_GLASS, BookmarkChatsUiState(name = LONG_NAME))

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsErrorMaterialPreview() =
    Host(UiStyle.MATERIAL, BookmarkChatsUiState(name = "Work", isLoading = false, loadFailed = true))

@PreviewNarrowScreens
@Composable
private fun BookmarkChatsErrorGlassPreview() =
    Host(UiStyle.LIQUID_GLASS, BookmarkChatsUiState(name = "Work", isLoading = false, loadFailed = true))
