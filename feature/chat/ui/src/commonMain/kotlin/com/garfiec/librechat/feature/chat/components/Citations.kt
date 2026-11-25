package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.content.CitationGroup
import com.garfiec.librechat.core.model.content.CitationIndex
import com.garfiec.librechat.core.model.content.mayHoldCitations
import com.garfiec.librechat.core.model.content.normalizeCitations

/** The inline-content id the annotator emits and the pill map resolves. */
internal val CitationGroup.inlineId: String get() = CITATION_INLINE_PREFIX + refs.joinToString(",") { it.key }

private const val CITATION_INLINE_PREFIX = "cite:"

/**
 * [segments] with their prose normalized against [index]: text blocks, the prose runs between
 * inline math, and table cells. Code and math are left alone. [streaming] holds back a partial
 * citation at the very end of the reply, which only the last segment can hold.
 */
internal fun List<MarkdownSegment>.withCitations(index: CitationIndex, streaming: Boolean): List<MarkdownSegment> {
    if (none(::segmentMayHoldCitations)) return this
    return mapIndexed { i, segment ->
        val tail = streaming && i == lastIndex
        when (segment) {
            is MarkdownSegment.TextBlock -> segment.copy(text = normalizeCitations(segment.text, index, tail))
            is MarkdownSegment.InlineLatexText -> segment.copy(
                segments = segment.segments.mapIndexed { j, inline ->
                    when (inline) {
                        is InlineSegment.Text ->
                            inline.copy(text = normalizeCitations(inline.text, index, tail && j == segment.segments.lastIndex))
                        is InlineSegment.Latex -> inline
                    }
                },
            )
            is MarkdownSegment.Table -> segment.copy(
                headers = segment.headers.map { normalizeCitations(it, index) },
                rows = segment.rows.map { row -> row.map { normalizeCitations(it, index) } },
            )
            is MarkdownSegment.CodeBlock, is MarkdownSegment.LatexBlock -> segment
        }
    }
}

private fun segmentMayHoldCitations(segment: MarkdownSegment): Boolean = when (segment) {
    is MarkdownSegment.TextBlock -> mayHoldCitations(segment.text)
    is MarkdownSegment.InlineLatexText -> segment.segments.any { it is InlineSegment.Text && mayHoldCitations(it.text) }
    is MarkdownSegment.Table -> (segment.headers + segment.rows.flatten()).any(::mayHoldCitations)
    is MarkdownSegment.CodeBlock, is MarkdownSegment.LatexBlock -> false
}
