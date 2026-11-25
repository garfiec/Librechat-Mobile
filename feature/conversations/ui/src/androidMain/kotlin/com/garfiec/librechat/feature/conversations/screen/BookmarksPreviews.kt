package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.components.BookmarkCard
import com.garfiec.librechat.feature.conversations.components.BookmarkDeleteDialog
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksUiState

// The Bookmarks index at the widths and translations that squeeze it: a long name, a card with no
// description, counts of 0, 1 and many; the empty, no-results, loading and error states; and two
// columns on a wide screen.

private val sampleBookmarks = listOf(
    ConversationTag(tag = "Work", description = "Client threads, status notes and the weekly summary drafts", count = 24),
    ConversationTag(
        tag = "Recipes to try when the whole family visits over the long weekend in spring",
        count = 1,
    ),
    ConversationTag(tag = "Reading list", description = "Papers and long reads to come back to", count = 0),
    ConversationTag(tag = "Taxes", count = 131),
)

private val noActions = BookmarkCardActions(onClick = {}, onEdit = {}, onDelete = {}, onMoveBy = { _, _ -> })

@Composable
private fun CardsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sampleBookmarks.forEach { BookmarkCard(bookmark = it, onClick = {}, onEdit = {}, onDelete = {}) }
            BookmarkCard(bookmark = sampleBookmarks.first(), onClick = {}, onEdit = {}, onDelete = {}, lifted = true)
        }
    }
}

@Composable
private fun GridHost(style: UiStyle, state: BookmarksUiState, height: Int = 640) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.height(height.dp).background(MaterialTheme.colorScheme.surface)) {
            BookmarksGrid(
                state = state,
                gridState = rememberLazyGridState(),
                contentPadding = PaddingValues(),
                onSearchQueryChange = {},
                onCreate = {},
                onRetry = {},
                onDrop = {},
                actions = noActions,
            )
        }
    }
}

private val loaded = BookmarksUiState(bookmarks = sampleBookmarks, isLoading = false)

private val searchable = BookmarksUiState(
    bookmarks = sampleBookmarks + (1..5).map { ConversationTag(tag = "Project $it", count = it) },
    isLoading = false,
)

@PreviewNarrowScreens
@Composable
private fun BookmarkCardsMaterialPreview() = CardsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun BookmarkCardsGlassPreview() = CardsHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun BookmarksIndexMaterialPreview() = GridHost(UiStyle.MATERIAL, loaded)

@PreviewNarrowScreens
@Composable
private fun BookmarksIndexGlassPreview() = GridHost(UiStyle.LIQUID_GLASS, loaded)

@PreviewNarrowScreens
@Composable
private fun BookmarksSearchMaterialPreview() = GridHost(UiStyle.MATERIAL, searchable)

@PreviewNarrowScreens
@Composable
private fun BookmarksSearchGlassPreview() = GridHost(UiStyle.LIQUID_GLASS, searchable)

@PreviewNarrowScreens
@Composable
private fun BookmarksEmptyMaterialPreview() = GridHost(UiStyle.MATERIAL, BookmarksUiState(isLoading = false))

@PreviewNarrowScreens
@Composable
private fun BookmarksEmptyGlassPreview() = GridHost(UiStyle.LIQUID_GLASS, BookmarksUiState(isLoading = false))

@PreviewNarrowScreens
@Composable
private fun BookmarksNoResultsMaterialPreview() =
    GridHost(UiStyle.MATERIAL, searchable.copy(searchQuery = "groundwater recharge"))

@PreviewNarrowScreens
@Composable
private fun BookmarksNoResultsGlassPreview() =
    GridHost(UiStyle.LIQUID_GLASS, searchable.copy(searchQuery = "groundwater recharge"))

@PreviewNarrowScreens
@Composable
private fun BookmarksLoadingMaterialPreview() = GridHost(UiStyle.MATERIAL, BookmarksUiState())

@PreviewNarrowScreens
@Composable
private fun BookmarksLoadingGlassPreview() = GridHost(UiStyle.LIQUID_GLASS, BookmarksUiState())

@PreviewNarrowScreens
@Composable
private fun BookmarksErrorMaterialPreview() =
    GridHost(UiStyle.MATERIAL, BookmarksUiState(isLoading = false, loadFailed = true))

@PreviewNarrowScreens
@Composable
private fun BookmarksErrorGlassPreview() =
    GridHost(UiStyle.LIQUID_GLASS, BookmarksUiState(isLoading = false, loadFailed = true))

// Two columns from 600dp: no drag there, so no reorder hint.
@Preview(name = "Two columns · Material", widthDp = 720, heightDp = 640, showBackground = true)
@Composable
private fun BookmarksTwoColumnMaterialPreview() = GridHost(UiStyle.MATERIAL, loaded)

@Preview(name = "Two columns · Glass", widthDp = 720, heightDp = 640, showBackground = true)
@Composable
private fun BookmarksTwoColumnGlassPreview() = GridHost(UiStyle.LIQUID_GLASS, loaded)

@PreviewNarrowScreens
@Composable
private fun BookmarkDeleteMaterialPreview() = DialogPreviewHost(UiStyle.MATERIAL) {
    BookmarkDeleteDialog(name = sampleBookmarks[1].tag.orEmpty(), chatCount = 3, onConfirm = {}, onDismiss = {})
}

@PreviewNarrowScreens
@Composable
private fun BookmarkDeleteGlassPreview() = DialogPreviewHost(UiStyle.LIQUID_GLASS) {
    BookmarkDeleteDialog(name = "Taxes", chatCount = 0, onConfirm = {}, onDismiss = {})
}
