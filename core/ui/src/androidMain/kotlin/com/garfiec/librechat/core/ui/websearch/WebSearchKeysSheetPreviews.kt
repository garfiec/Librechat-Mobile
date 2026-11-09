package com.garfiec.librechat.core.ui.websearch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

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
