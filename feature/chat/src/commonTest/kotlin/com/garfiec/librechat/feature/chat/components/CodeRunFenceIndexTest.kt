package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.components.artifact.ArtifactSegment
import com.garfiec.librechat.feature.chat.components.artifact.detectArtifacts
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fence's block index must be the one web's `CodeBlockContext` gives it, or a run made on one
 * client shows under another block on the other.
 */
class CodeRunFenceIndexTest {

    private fun indices(text: String) =
        parseMarkdownSegments(text).filterIsInstance<MarkdownSegment.CodeBlock>().map { it.language to it.fenceIndex }

    @Test
    fun mathMermaidAndEmptyFencesTakeNoIndex() {
        val text = listOf(
            "```py\nprint(1)\n```",
            "```mermaid\ngraph TD; A-->B\n```",
            "```math\nx^2\n```",
            "```\n```",
            "```js\nconsole.log(2)\n```",
        ).joinToString("\n\n")

        assertEquals(
            listOf("py" to 0, "mermaid" to null, "js" to 1),
            indices(text).filter { it.first != null },
        )
        assertEquals(2, webCodeBlockCount(text))
    }

    @Test
    fun latexFencesCountThoughDrawnAsMath() {
        val text = "```latex\nx^2\n```\n\n```py\nprint(1)\n```"

        assertEquals(listOf("py" to 1), indices(text))
        assertEquals(2, webCodeBlockCount(text))
    }

    @Test
    fun anHtmlBlockLiftedOutOfProseTakesNoIndex() {
        val text = "<div>\nhello\n</div>\n\n```py\nprint(1)\n```"

        assertEquals(listOf("html" to null, "py" to 0), indices(text))
    }

    @Test
    fun anArtifactsFencesDoNotMoveTheReplysBlocks() {
        val text = "```py\nprint(0)\n```\n\n:::artifact{identifier=\"doc\" type=\"text/plain\" title=\"Doc\"}\n" +
            "```js\nconsole.log(1)\n```\n:::\n\n```py\nprint(1)\n```"
        val chunks = detectArtifacts(text)
        val bases = codeRunBases(chunks.map { (it as? ArtifactSegment.Text)?.text })

        assertEquals(true, chunks.any { it is ArtifactSegment.ArtifactReference })
        val last = chunks.indexOfLast { it is ArtifactSegment.Text }
        // The fence after the artifact is the reply's second block, whatever the artifact holds.
        assertEquals(1, bases[last])
    }
}
