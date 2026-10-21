package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksUiState

private fun sampleLink(shareId: String, title: String?, createdAt: String?) = SharedLinkDisplayData(
    shareId = shareId,
    conversationId = "conversation-$shareId",
    title = title,
    createdAt = createdAt,
    url = "https://chat.example.org/share/$shareId",
)

private val sampleLinks = listOf(
    sampleLink("9fK2xQ7aLm0pR3tY", "Trip budget for Lisbon", "2026-10-18T09:12:00Z"),
    sampleLink(
        "b81Lr4QzT0aWm2Xc",
        "Refactoring the payment reconciliation service into smaller modules with clear ownership",
        "2026-10-02T15:40:00Z",
    ),
    sampleLink("Uu72kfP0s9dLq1Ze", null, "2026-09-29T08:00:00Z"),
    sampleLink("Kc0r9tiN4mbW5yRe", "Kotlin coroutine cancellation rules", null),
)

@PreviewNarrowScreens
@Composable
private fun SharedLinkCardsMaterialPreview() = SharedLinkCardsHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SharedLinkCardsGlassPreview() = SharedLinkCardsHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SharedLinksPageMaterialPreview() =
    SharedLinksPageHost(SharedLinksUiState(links = sampleLinks, isLoading = false, loadMoreFailed = true, searchEnabled = true))

@PreviewNarrowScreens
@Composable
private fun SharedLinksPageGlassPreview() = SharedLinksPageHost(
    SharedLinksUiState(links = sampleLinks, isLoading = false, loadMoreFailed = true, searchEnabled = true),
    style = UiStyle.LIQUID_GLASS,
)

@PreviewNarrowScreens
@Composable
private fun SharedLinksPlaceholdersMaterialPreview() = SharedLinksPageHost(SharedLinksUiState(searchEnabled = true))

@PreviewNarrowScreens
@Composable
private fun SharedLinksPlaceholdersGlassPreview() =
    SharedLinksPageHost(SharedLinksUiState(searchEnabled = true), style = UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SharedLinksEmptyMaterialPreview() = SharedLinksPageHost(SharedLinksUiState(isLoading = false))

@PreviewNarrowScreens
@Composable
private fun SharedLinksEmptyGlassPreview() =
    SharedLinksPageHost(SharedLinksUiState(isLoading = false), style = UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SharedLinksErrorMaterialPreview() = SharedLinksPageHost(SharedLinksUiState(isLoading = false, loadFailed = true))

@PreviewNarrowScreens
@Composable
private fun SharedLinksErrorGlassPreview() =
    SharedLinksPageHost(SharedLinksUiState(isLoading = false, loadFailed = true), style = UiStyle.LIQUID_GLASS)

private val noMatch = SharedLinksUiState(
    isLoading = false,
    searchEnabled = true,
    searchQuery = "Kreuzberg Wohnungsbesichtigung",
    shownQuery = "Kreuzberg Wohnungsbesichtigung",
    sort = SharedLinksSort.TITLE,
)

@PreviewNarrowScreens
@Composable
private fun SharedLinksNoMatchMaterialPreview() = SharedLinksPageHost(noMatch)

@PreviewNarrowScreens
@Composable
private fun SharedLinksNoMatchGlassPreview() = SharedLinksPageHost(noMatch, style = UiStyle.LIQUID_GLASS)

@Composable
private fun SharedLinkCardsHost(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                sampleLinks.forEach { link ->
                    SharedLinkCard(link = link, onClick = {}, onOpenChat = {}, onCopyLink = {}, onDelete = {})
                }
                SharedLinkCardPlaceholder()
            }
        }
    }
}

/** The page's grid as the screen lays it out on a phone (one column), for one state. */
@Composable
private fun SharedLinksPageHost(state: SharedLinksUiState, dark: Boolean = false, style: UiStyle = UiStyle.MATERIAL) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sharedLinksItems(
                    uiState = state,
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onSortChange = {},
                    onRetry = {},
                    onLoadMore = {},
                    card = { link ->
                        SharedLinkCard(link = link, onClick = {}, onOpenChat = {}, onCopyLink = {}, onDelete = {})
                    },
                )
            }
        }
    }
}
