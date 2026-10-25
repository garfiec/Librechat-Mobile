package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.WebSearchData
import com.garfiec.librechat.core.model.WebSearchSource
import com.garfiec.librechat.core.model.content.CitationIndex
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Citations through the renderer's path: segment normalization, then [citationAnnotate] over a real
 * parse. An annotator that claims nothing builds and renders like a working one, minus the pills, so
 * these walk the AST the renderer walks and assert on what the hook emitted.
 */
class CitationRenderingTest {

    private val parser = MarkdownParser(GFMFlavourDescriptor())

    private val index = CitationIndex.from(
        listOf(
            Attachment(
                type = "web_search",
                webSearch = WebSearchData(turn = 0, organic = List(3) { WebSearchSource(link = "https://site$it.com/a", title = "T$it") }),
            ),
        ),
    )

    private val a = "\\ue202"

    /** Offers every leaf to [annotate] as the renderer's traversal does, keeping only claimed output. */
    private fun annotateLeaves(
        markdown: String,
        annotate: AnnotatedString.Builder.(String, ASTNode) -> Boolean = citationAnnotate,
    ): Pair<AnnotatedString, Int> {
        var claimed = 0
        val built = buildAnnotatedString {
            fun walk(node: ASTNode) {
                if (node.children.isEmpty()) {
                    if (annotate(markdown, node)) claimed++
                    return
                }
                node.children.forEach(::walk)
            }
            walk(parser.buildMarkdownTreeFromString(markdown))
        }
        return built to claimed
    }

    private fun AnnotatedString.inlineIds() = getStringAnnotations(0, length).map { it.item }

    @Test
    fun `segments normalize prose and table cells but not code`() {
        val segments = parseMarkdownSegments(
            "Intro.${a}turn0search0\n\n| A | B |\n|---|---|\n| x${a}turn0search1 | y |\n\n```\n${a}turn0search0\n```",
        ).withCitations(index, streaming = false)
        assertEquals("Intro.\uE202turn0search0", (segments[0] as MarkdownSegment.TextBlock).text)
        assertEquals("x\uE202turn0search1", (segments[1] as MarkdownSegment.Table).rows[0][0])
        assertEquals("${a}turn0search0", (segments[2] as MarkdownSegment.CodeBlock).code)
    }

    @Test
    fun `only the last segment of a streaming reply holds back its tail`() {
        val segments = parseMarkdownSegments("First.${a}turn0search0\n\n```\nx\n```\n\nLast.${a}turn0sea")
            .withCitations(index, streaming = true)
        assertEquals("First.\uE202turn0search0", (segments.first() as MarkdownSegment.TextBlock).text)
        assertEquals("Last.", (segments.last() as MarkdownSegment.TextBlock).text)
    }

    @Test
    fun `a standalone anchor becomes one placeholder after its sentence`() {
        val (built, claimed) = annotateLeaves("Statement.\uE202turn0search1 more")
        assertEquals(1, claimed)
        assertEquals(listOf("cite:0search1"), built.inlineIds())
        assertEquals("Statement.  more", built.text)
    }

    @Test
    fun `a group is one placeholder carrying every anchor`() {
        val (built, _) = annotateLeaves("Claim \uE200\uE202turn0search0\uE202turn0search2\uE201, next.")
        assertEquals(listOf("cite:0search0,0search2"), built.inlineIds())
    }

    @Test
    fun `anchors inside bold text and after a link are claimed`() {
        val (built, claimed) = annotateLeaves("**bold\uE202turn0search0** and [link](https://x.y)\uE202turn0search1")
        assertEquals(2, claimed)
        assertEquals(listOf("cite:0search0", "cite:0search1"), built.inlineIds())
    }

    @Test
    fun `an anchor in a code span is not claimed`() {
        val (_, claimed) = annotateLeaves("Run `x\uE202turn0search0` now")
        assertEquals(0, claimed)
    }

    @Test
    fun `the cursor in a citation leaf is emitted with it`() {
        val (built, claimed) = annotateLeaves(
            withStreamingCursor("Claim.\uE202turn0search0"),
        ) { content, node -> citationAnnotate(this, content, node) || streamingCursorAnnotate(this, content, node) }
        assertEquals(1, claimed)
        assertEquals(listOf("cite:0search0", STREAMING_CURSOR_INLINE_ID), built.inlineIds())
    }

    @Test
    fun `prose without anchors is left to the renderer`() {
        val (_, claimed) = annotateLeaves("Plain arr[1] and **bold**")
        assertEquals(0, claimed)
    }

    @Test
    fun `search counts the text without citations`() {
        val text = "Apple event${a}turn0search0 and turn${a}turn0search1 done"
        assertEquals(0, countMarkdownOccurrences(text, "search0"))
        assertEquals(0, countMarkdownOccurrences(text, "ue202"))
        assertEquals(1, countMarkdownOccurrences(text, "event"))
        assertEquals(1, countMarkdownOccurrences(text, "done"))
    }
}
