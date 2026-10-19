package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// A small tablet, a phone on its side, and a tablet in portrait: where the list and preview split.
@Preview(name = "600dp", widthDp = 600, heightDp = 720, showBackground = true)
@Preview(name = "760dp · landscape body (240dp)", widthDp = 760, heightDp = 240, showBackground = true)
@Preview(name = "800dp · fr", widthDp = 800, heightDp = 720, locale = "fr", showBackground = true)
@Composable
private fun SearchPanesPreview() = PreviewSurface(UiStyle.MATERIAL) { SearchPanesHost() }

@Preview(name = "pane 340dp", widthDp = 340, heightDp = 480, showBackground = true)
@Preview(name = "pane 380dp · fr", widthDp = 380, heightDp = 480, locale = "fr", showBackground = true)
@Composable
private fun SearchPreviewPanePreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { PreviewPaneHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** The screen's body as SearchScreen lays it out, with the selected hit in the pane. */
@Composable
private fun SearchPanesHost() = ProvideDateTimeFormat(DateTimeFormatPrefs()) {
    val listState = rememberLazyListState()
    SearchPanes(
        list = { twoPane, modifier ->
            SearchResultsList(
                state = previewState,
                twoPane = twoPane,
                onHitClick = {},
                onOpenSelection = {},
                onMoveSelection = {},
                onConversationClick = {},
                onToggleGroup = {},
                onScanMore = {},
                listState = listState,
                modifier = modifier,
            )
        },
        preview = { modifier -> PreviewPaneHost(modifier) },
    )
}

@Composable
private fun PreviewPaneHost(modifier: Modifier = Modifier) = ProvideDateTimeFormat(DateTimeFormatPrefs()) {
    SearchPreviewPane(
        preview = previewState.preview,
        chatPreview = null,
        agentNames = emptyMap(),
        onStep = {},
        onOpen = {},
        onOpenConversation = {},
        modifier = modifier,
    )
}

private fun snippet(text: String, match: String) = HighlightedSnippet(
    text = text,
    highlights = text.indexOf(match).let { if (it < 0) emptyList() else listOf(it until it + match.length) },
    truncatedStart = false,
    truncatedEnd = false,
)

private fun hit(id: String, text: String, user: Boolean) = MessageSearchHit(
    accountId = "preview",
    messageId = id,
    conversationId = "lisbon",
    conversationTitle = "Trip to Lisbon in May",
    createdAt = 1_747_000_000_000,
    isCreatedByUser = user,
    model = "gpt-5",
    endpoint = "openAI",
    snippet = snippet(text, "tram"),
    source = HitSource.Local,
)

private val hits = listOf(
    hit("m1", "Which tram goes up to the castle, and how early should we get on it?", user = true),
    hit(
        "m2",
        "Take tram 28 from Martim Moniz before 9am: it fills up fast, and the queue at the castle is " +
            "shortest at opening. Tram 12 loops the same hills if 28 is packed.",
        user = false,
    ),
    hit("m3", "Is the tram worth it with a stroller, or should we walk?", user = true),
)

private val previewState = SearchUiState(
    query = "tram",
    searched = true,
    groups = listOf(
        SearchResultGroup(conversationId = "lisbon", title = "Trip to Lisbon in May", hits = hits, fromServer = false),
    ),
    messageCount = hits.size,
    selectedMessageId = "m2",
    preview = SearchPreview(
        hit = hits[1],
        index = 1,
        count = 12,
        thread = hits.map { PreviewTurn(messageId = it.messageId, snippet = it.snippet, isUser = it.isCreatedByUser) },
        hitIndex = 1,
    ),
)
