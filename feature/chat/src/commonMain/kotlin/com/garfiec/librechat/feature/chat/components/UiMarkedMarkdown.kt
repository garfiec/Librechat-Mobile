package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.components.mcpapp.LegacyUiResourceBlock
import com.garfiec.librechat.feature.chat.util.UiMarkerSegment
import com.garfiec.librechat.feature.chat.util.splitUiResourceMarkers

/**
 * [MarkdownContent] for reply text that may carry legacy MCP-UI `\ui{…}` markers: the markers never
 * show; each renders its resources ([LegacyUiResourceBlock]) where it stood. Text without one goes
 * straight to [MarkdownContent], unchanged.
 *
 * Search offsets follow [countUiMarkedOccurrences], which the ViewModel-side walk uses too.
 */
@Composable
internal fun UiMarkedMarkdownContent(
    text: String,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    searchQuery: String?,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    streaming: Boolean,
    trailingCursor: Boolean,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { splitUiResourceMarkers(text) }
    val only = segments.singleOrNull()
    if (only is UiMarkerSegment.Text) {
        MarkdownContent(
            only.text,
            modifier,
            fontSizeMultiplier,
            useKatex,
            searchQuery,
            searchFocusedOccurrence,
            onFocusedOccurrencePosition,
            streaming = streaming,
            trailingCursor = trailingCursor,
        )
        return
    }
    val offsets = remember(segments, searchQuery) {
        IntArray(segments.size).also { out ->
            if (!searchQuery.isNullOrBlank()) {
                var acc = 0
                segments.forEachIndexed { i, segment ->
                    out[i] = acc
                    if (segment is UiMarkerSegment.Text) acc += countMarkdownOccurrences(segment.text, searchQuery)
                }
            }
        }
    }
    val runBases = rememberCodeRunBases(segments) { segments.map { (it as? UiMarkerSegment.Text)?.text } }
    Column(modifier = modifier) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is UiMarkerSegment.Text -> ProvideCodeRunBase(runBases, index) {
                    MarkdownContent(
                        segment.text,
                        Modifier.fillMaxWidth(),
                        fontSizeMultiplier,
                        useKatex,
                        searchQuery,
                        searchFocusedOccurrence - offsets[index],
                        onFocusedOccurrencePosition,
                        streaming = streaming,
                        trailingCursor = trailingCursor && index == segments.lastIndex,
                    )
                }
                // A resource is the tool's own view, not message prose: never part of a selection.
                is UiMarkerSegment.Resources -> DisableSelection {
                    LegacyUiResourceBlock(
                        ids = segment.ids,
                        streaming = streaming,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (trailingCursor && segments.lastOrNull() !is UiMarkerSegment.Text) {
            StandaloneStreamingCursor(fontSizeMultiplier = fontSizeMultiplier)
        }
    }
}

/** Occurrences in reply text as [UiMarkedMarkdownContent] draws it: markers contribute none. */
internal fun countUiMarkedOccurrences(text: String, query: String): Int =
    splitUiResourceMarkers(text).sumOf { segment ->
        if (segment is UiMarkerSegment.Text) countMarkdownOccurrences(segment.text, query) else 0
    }
