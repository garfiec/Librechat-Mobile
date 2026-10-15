package com.garfiec.librechat.feature.conversations.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.core.ui.theme.searchMatchColor

private const val ELLIPSIS = "…"

/**
 * A search hit's snippet with its matches marked. The ranges were computed off the main thread by
 * the repository; this only maps them onto spans, once per snippet.
 */
@Composable
fun SnippetText(
    snippet: HighlightedSnippet,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 2,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    // Find-in-chat's match colour, so a match looks the same here and in the chat it opens.
    val highlight = searchMatchColor(isSurfaceDark())
    val onHighlight = MaterialTheme.colorScheme.onSurface
    val text = remember(snippet, highlight, onHighlight) { snippet.annotated(highlight, onHighlight) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout,
        modifier = modifier,
    )
}

@Composable
fun HighlightedTitle(
    title: String,
    highlights: List<IntRange>?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val snippet = remember(title, highlights) {
        HighlightedSnippet(text = title, highlights = highlights.orEmpty(), truncatedStart = false, truncatedEnd = false)
    }
    SnippetText(snippet = snippet, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = modifier)
}

private fun HighlightedSnippet.annotated(highlight: Color, onHighlight: Color): AnnotatedString = buildAnnotatedString {
    val offset = if (truncatedStart) ELLIPSIS.length else 0
    if (truncatedStart) append(ELLIPSIS)
    append(text)
    if (truncatedEnd) append(ELLIPSIS)
    val style = SpanStyle(color = onHighlight, background = highlight)
    highlights.forEach { range -> addStyle(style, range.first + offset, range.last + 1 + offset) }
}
