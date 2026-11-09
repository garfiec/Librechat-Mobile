package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.WebSearchKeysRow
import com.garfiec.librechat.feature.chat.util.WebSearchKeysState

/** Search provider and page reader left to the user, as on a server that pins nothing. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysTwoSectionsMaterialPreview() = KeysSurface(UiStyle.MATERIAL, twoSections)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysTwoSectionsGlassPreview() = KeysSurface(UiStyle.LIQUID_GLASS, twoSections)

/** The admin pinned SearXNG: its choice is fixed, only its inputs are asked for. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysPinnedMaterialPreview() = KeysSurface(UiStyle.MATERIAL, pinned)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysPinnedGlassPreview() = KeysSurface(UiStyle.LIQUID_GLASS, pinned)

/** Keys already set up: empty fields keep them, and Remove keys is offered. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysAuthenticatedMaterialPreview() = KeysSurface(UiStyle.MATERIAL, authenticated)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysAuthenticatedGlassPreview() = KeysSurface(UiStyle.LIQUID_GLASS, authenticated)

/** The "+" sheet's row while Web Search is on and the built-in search stands in. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetupMaterialPreview() = RowSurface(UiStyle.MATERIAL, setupRow)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetupGlassPreview() = RowSurface(UiStyle.LIQUID_GLASS, setupRow)

/** The row once the user's keys work. */
@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetMaterialPreview() = RowSurface(UiStyle.MATERIAL, setRow)

@PreviewNarrowScreens
@Composable
private fun WebSearchKeysRowSetGlassPreview() = RowSurface(UiStyle.LIQUID_GLASS, setRow)

@Composable
private fun RowSurface(style: UiStyle, row: WebSearchKeysRow) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 16.dp)) {
            WebSearchKeysActionRow(row = row, onClick = {})
        }
    }
}

@Composable
private fun KeysSurface(style: UiStyle, keys: WebSearchKeysState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            WebSearchKeysSheetContent(
                keys = keys,
                revealed = emptySet(),
                onRevealedChange = { _, _ -> },
                onBusyChange = {},
                onSave = { true },
                onRemove = { true },
                onDismiss = {},
            )
        }
    }
}

private val setupRow = WebSearchKeysRow(authenticated = false, fallbackProvider = "OpenAI")
private val setRow = WebSearchKeysRow(authenticated = true, summary = "Serper · Firecrawl")

private val twoSections = WebSearchKeysState(
    authenticated = false,
    userCategories = listOf("providers", "scrapers"),
    rerankerType = "none",
)

private val pinned = WebSearchKeysState(
    authenticated = false,
    userCategories = listOf("providers"),
    pinnedSearchProvider = "searxng",
)

private val authenticated = WebSearchKeysState(
    authenticated = true,
    userCategories = listOf("providers", "scrapers", "rerankers"),
    searchProvider = "serper",
    scraperProvider = "firecrawl",
    rerankerType = "jina",
)
