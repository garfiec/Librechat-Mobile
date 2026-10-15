package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

// Find-in-page over rendered markdown. With find open, a message keeps its markdown: matches are
// marked in the text the renderer draws, not in the source, so `**bold**` is found as "bold" and a
// link's URL, which is never drawn, is not found at all.
//
// The renderer draws a markdown block as one Text per paragraph, heading or bare text node, each
// built by the library's `buildMarkdownAnnotatedString`. [markdownRuns] walks the same tree in the
// same order and builds the same strings, so the count it gives SearchMatchEnumeration and the
// marks the overridden components draw are numbered by one walk. Code fences, tables, images and
// rules draw no run: they contribute nothing here (closed fences and tables are split out before
// markdown is parsed, and searched as segments of their own).

/** One Text the renderer draws: its displayed text, and the start in the source of the node drawn. */
internal class MarkdownRun(val nodeStart: Int, val text: String)

/** The runs of text [markdown] displays, in render order. Cached: a find counts them per keystroke. */
internal fun markdownRuns(markdown: String): List<MarkdownRun> = MarkdownRunCache.getOrPut(markdown) {
    val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    buildList { collectRuns(tree, markdown, this) }
}

/** Matches of [query] in what [markdown] displays (see [markdownRuns]). */
internal fun countDisplayedOccurrences(markdown: String, query: String): Int {
    if (markdown.isBlank() || query.isBlank() || !mayContain(markdown, query)) return 0
    return markdownRuns(markdown).sumOf { countOccurrences(it.text, query) }
}

private fun collectRuns(node: ASTNode, markdown: String, into: MutableList<MarkdownRun>) {
    val contentNode = runContentNode(node)
    when {
        contentNode != null -> into += MarkdownRun(node.startOffset, displayedText(markdown, contentNode))
        node.type == MarkdownTokenTypes.TEXT -> into += MarkdownRun(node.startOffset, node.getUnescapedTextInNode(markdown))
        node.type in DRAWS_NO_RUN -> Unit
        // As the renderer does with a node it has no component for: draw its children.
        else -> node.children.forEach { collectRuns(it, markdown, into) }
    }
}

/** The node whose children a paragraph or heading component builds its text from; null for others. */
private fun runContentNode(node: ASTNode): ASTNode? = when (node.type) {
    MarkdownElementTypes.PARAGRAPH -> node
    in ATX_HEADINGS -> node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT) ?: node
    MarkdownElementTypes.SETEXT_1, MarkdownElementTypes.SETEXT_2 ->
        node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT) ?: node
    else -> null
}

private fun displayedText(markdown: String, contentNode: ASTNode): String =
    buildAnnotatedString { buildMarkdownAnnotatedString(markdown, contentNode, CountingSettings) }.text

// Styles don't change the text built; only the annotator could, and the renderer's is a no-op while
// find is open (the streaming cursor's never meets a query).
private val CountingSettings = DefaultAnnotatorSettings(
    linkTextSpanStyle = TextLinkStyles(),
    codeSpanStyle = SpanStyle(),
    annotator = NoOpMarkdownAnnotator,
)

private val ATX_HEADINGS = setOf(
    MarkdownElementTypes.ATX_1,
    MarkdownElementTypes.ATX_2,
    MarkdownElementTypes.ATX_3,
    MarkdownElementTypes.ATX_4,
    MarkdownElementTypes.ATX_5,
    MarkdownElementTypes.ATX_6,
)

private val DRAWS_NO_RUN: Set<IElementType> = setOf(
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.IMAGE,
    MarkdownTokenTypes.HORIZONTAL_RULE,
    GFMElementTypes.TABLE,
)

/** What a markdown block needs to mark its matches: the query, the focused one, and where to report it. */
internal class MarkdownSearch(
    val query: String,
    /** The focused occurrence within this markdown block, or -1. */
    val focused: Int,
    val onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)?,
    /** For each run's node start, the number of matches in the runs before it. */
    val firstMatchByRun: Map<Int, Int>,
)

/** [MarkdownSearch] for [markdown], or null with no query. */
@Composable
internal fun rememberMarkdownSearch(
    markdown: String,
    query: String?,
    focused: Int,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)?,
): MarkdownSearch? {
    if (query.isNullOrBlank()) return null
    val firstMatchByRun = remember(markdown, query) {
        var before = 0
        markdownRuns(markdown).associate { run ->
            (run.nodeStart to before).also { before += countOccurrences(run.text, query) }
        }
    }
    return remember(query, focused, onFocusedMatchPosition, firstMatchByRun) {
        MarkdownSearch(query, focused, onFocusedMatchPosition, firstMatchByRun)
    }
}

/** The m3 renderer's own components (it supplies its checkbox), for markdown drawn with no find open. */
internal val ChatMarkdownComponents: MarkdownComponents = markdownComponents(
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
)

/** Components that draw paragraphs, headings and text with [search]'s matches marked. */
internal fun searchMarkdownComponents(search: MarkdownSearch): MarkdownComponents = markdownComponents(
    text = { model ->
        MarkedRun(model, model.typography.text, search) { model.node.getUnescapedTextInNode(model.content) }
    },
    paragraph = { model -> MarkedRun(model, model.typography.paragraph, search, contentNode = model.node) },
    heading1 = { model -> MarkedHeading(model, model.typography.h1, search, MarkdownTokenTypes.ATX_CONTENT) },
    heading2 = { model -> MarkedHeading(model, model.typography.h2, search, MarkdownTokenTypes.ATX_CONTENT) },
    heading3 = { model -> MarkedHeading(model, model.typography.h3, search, MarkdownTokenTypes.ATX_CONTENT) },
    heading4 = { model -> MarkedHeading(model, model.typography.h4, search, MarkdownTokenTypes.ATX_CONTENT) },
    heading5 = { model -> MarkedHeading(model, model.typography.h5, search, MarkdownTokenTypes.ATX_CONTENT) },
    heading6 = { model -> MarkedHeading(model, model.typography.h6, search, MarkdownTokenTypes.ATX_CONTENT) },
    setextHeading1 = { model -> MarkedHeading(model, model.typography.h1, search, MarkdownTokenTypes.SETEXT_CONTENT) },
    setextHeading2 = { model -> MarkedHeading(model, model.typography.h2, search, MarkdownTokenTypes.SETEXT_CONTENT) },
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
)

@Composable
private fun MarkedHeading(model: MarkdownComponentModel, style: TextStyle, search: MarkdownSearch, contentType: IElementType) {
    MarkedRun(
        model = model,
        style = style,
        search = search,
        contentNode = model.node.findChildOfType(contentType) ?: model.node,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * One run drawn as the library draws it, with the matches marked and the focused one reported for
 * scrolling. [plainText] is for a bare text node, which the library draws unparsed.
 */
@Composable
private fun MarkedRun(
    model: MarkdownComponentModel,
    style: TextStyle,
    search: MarkdownSearch,
    modifier: Modifier = Modifier,
    contentNode: ASTNode? = null,
    plainText: (() -> String)? = null,
) {
    val settings = annotatorSettings()
    val isDark = isSurfaceDark()
    val focused = search.focused.takeIf { it >= 0 }?.let { it - (search.firstMatchByRun[model.node.startOffset] ?: 0) } ?: -1
    val (marked, focusedRange) = remember(model.content, model.node, style, search.query, focused, isDark) {
        val base = buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            if (plainText != null) {
                append(plainText())
            } else {
                buildMarkdownAnnotatedString(model.content, contentNode ?: model.node, settings)
            }
            pop()
        }
        addSearchSpans(base, search.query, focused, isDark) to findOccurrenceRange(base.text, search.query, focused)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    MarkdownText(
        content = marked,
        node = model.node,
        modifier = modifier.reportFocusedMatchPosition(layout, focusedRange, search.onFocusedMatchPosition),
        style = style,
        onTextLayout = { result, _ -> layout = result },
        sourceContent = model.content,
    )
}

/** Runs per markdown source, least recently used dropped first. Shared by every thread that counts. */
private object MarkdownRunCache {
    private const val MAX_ENTRIES = 256
    private val lock = SynchronizedObject()
    private val entries = LinkedHashMap<String, List<MarkdownRun>>()

    fun getOrPut(markdown: String, compute: () -> List<MarkdownRun>): List<MarkdownRun> {
        synchronized(lock) {
            entries.remove(markdown)?.let { cached ->
                entries[markdown] = cached
                return cached
            }
        }
        val computed = compute()
        synchronized(lock) {
            entries[markdown] = computed
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
        return computed
    }
}
