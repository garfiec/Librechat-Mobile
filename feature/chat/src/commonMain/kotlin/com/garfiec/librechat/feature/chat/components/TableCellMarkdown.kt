package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import com.garfiec.librechat.core.model.content.CITATION_ANCHOR
import com.garfiec.librechat.core.model.content.CitationIndex
import com.garfiec.librechat.core.model.content.CitationPiece
import com.garfiec.librechat.core.model.content.citationPieces
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

/**
 * A table cell as the table shows it: inline markdown (bold, italic, strikethrough, code, links)
 * rendered as spans and its markers gone, through the same parser as the rest of the message.
 *
 * A cell the parser reads as a block rather than a paragraph (`1. Step`, `# Tag`) comes back empty
 * from the library, so it falls back to the raw text instead of vanishing.
 */
internal fun tableCellAnnotatedString(cell: String, settings: AnnotatorSettings): AnnotatedString {
    if (cell.isBlank()) return AnnotatedString(cell)
    val rendered = cell.buildMarkdownAnnotatedString(TextStyle.Default, settings, TableCellFlavour)
    return if (rendered.text.isBlank()) AnnotatedString(cell) else rendered
}

/**
 * The text a cell displays. Search counts and highlights against this, never the raw cell, so a
 * match numbered by [tableCellTexts] lands on the same characters the table draws.
 */
internal fun tableCellPlainText(cell: String): String = tableCellAnnotatedString(cell, PlainCellSettings).text

private val TableCellFlavour = GFMFlavourDescriptor()

/**
 * Nodes the library's annotator would drop from a cell. GFM reads `$5 and $` as inline math and the
 * annotator renders no math, so a price range would lose everything between its dollar signs; `<br>`
 * is the only way to break a line inside a table row, and dropping it would run the two lines
 * together. A cell's image has no inline content to resolve to, so it keeps its source rather than
 * showing a bare URL.
 *
 * Shared by the themed and the plain settings: both must produce the same text, or search numbering
 * drifts off the characters the table draws.
 */
private val TableCellAnnotator: MarkdownAnnotator =
    markdownAnnotator { content, child -> tableCellAnnotate(content, child) }

private fun AnnotatedString.Builder.tableCellAnnotate(content: String, child: ASTNode): Boolean =
    when (child.type) {
        GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH, MarkdownElementTypes.IMAGE -> {
            append(child.getTextInNode(content))
            true
        }
        MarkdownTokenTypes.HTML_TAG -> {
            val lineBreak = isLineBreakTag(child.getTextInNode(content))
            if (lineBreak) append('\n')
            lineBreak
        }
        else -> false
    }

/**
 * [TableCellAnnotator] plus citations. A cell is a plain annotated string with no inline content, so
 * a group draws as its pill label in a tinted span that opens the same sources sheet as a pill.
 * Themed rendering only: while finding, cells arrive with their citations already stripped, which is
 * the text [tableCellPlainText] numbers.
 */
private fun tableCellCitationAnnotator(
    index: CitationIndex,
    sheet: CitationSheetState,
    style: TextLinkStyles,
): MarkdownAnnotator = markdownAnnotator { content, child ->
    val text = if (child.type == MarkdownTokenTypes.TEXT) child.getUnescapedTextInNode(content) else null
    if (text == null || CITATION_ANCHOR !in text) {
        tableCellAnnotate(content, child)
    } else {
        for (piece in citationPieces(text)) {
            when (piece) {
                is CitationPiece.Text -> append(piece.text)
                is CitationPiece.Group -> {
                    val sources = index.resolve(piece.group)
                    if (sources.isNotEmpty()) {
                        append(' ')
                        withLink(LinkAnnotation.Clickable(piece.group.inlineId, style) { sheet.open(sources) }) {
                            append(citationPillLabel(sources))
                        }
                    }
                }
            }
        }
        true
    }
}

private val LineBreakTags = setOf("<br>", "<br/>")

private fun isLineBreakTag(tag: CharSequence): Boolean =
    tag.filterNot { it.isWhitespace() }.toString().lowercase() in LineBreakTags

private val PlainCellSettings = DefaultAnnotatorSettings(
    linkTextSpanStyle = TextLinkStyles(),
    codeSpanStyle = SpanStyle(),
    highlightSpanStyle = SpanStyle(),
    annotator = TableCellAnnotator,
)

@Composable
internal fun rememberTableCellAnnotatorSettings(): AnnotatorSettings {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightText = MaterialTheme.colorScheme.onSecondaryContainer
    val highlightBackground = MaterialTheme.colorScheme.secondaryContainer
    val citationText = MaterialTheme.colorScheme.onSurfaceVariant
    val uriHandler = LocalUriHandler.current
    val citations = LocalMarkdownMedia.current.citations
    val sheet = LocalCitationSheet.current
    return remember(linkColor, codeBackground, highlightText, highlightBackground, citationText, uriHandler, citations, sheet) {
        val annotator = if (citations.isEmpty) {
            TableCellAnnotator
        } else {
            val citationStyle = SpanStyle(color = citationText, background = codeBackground, fontSize = 0.85.em)
            tableCellCitationAnnotator(citations, sheet, TextLinkStyles(citationStyle))
        }
        DefaultAnnotatorSettings(
            linkTextSpanStyle = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            codeSpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
            highlightSpanStyle = SpanStyle(color = highlightText, background = highlightBackground),
            annotator = annotator,
            linkInteractionListener = { link ->
                (link as? LinkAnnotation.Url)?.url?.let { url -> runCatching { uriHandler.openUri(url) } }
            },
        )
    }
}

/** A table's header and row cells as [tableCellAnnotatedString] renders them. */
internal class RenderedTableCells(val headers: List<AnnotatedString>, val rows: List<List<AnnotatedString>>)

/**
 * Renders every cell of a table, re-parsing only cells whose text changed since the last call: a
 * streaming table's rows are a new list on every flush, but only its last cell has grown.
 */
@Composable
internal fun rememberRenderedTableCells(headers: List<String>, rows: List<List<String>>): RenderedTableCells {
    val settings = rememberTableCellAnnotatorSettings()
    val cache = remember(settings) { TableCellCache(settings) }
    return remember(headers, rows, cache) { cache.render(headers, rows) }
}

private class TableCellCache(private val settings: AnnotatorSettings) {
    // Only the last render's cells are kept, so the partial cells a stream passes through don't pile up.
    private var previous: Map<String, AnnotatedString> = emptyMap()

    fun render(headers: List<String>, rows: List<List<String>>): RenderedTableCells {
        val current = HashMap<String, AnnotatedString>()
        val cell = { text: String -> current.getOrPut(text) { previous[text] ?: tableCellAnnotatedString(text, settings) } }
        val rendered = RenderedTableCells(headers.map(cell), rows.map { row -> row.map(cell) })
        previous = current
        return rendered
    }
}
