package com.garfiec.librechat.core.ui.components.pullrequest

import com.garfiec.librechat.core.model.pullrequest.PullRequestIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class PullRequestGlyphsTest {

    /** Every glyph's path parses at class init, and each state has its own. */
    @Test
    fun everyStateHasADistinctGlyph() {
        val glyphs = PullRequestIcon.entries.map(PullRequestGlyphs::of)
        assertEquals(PullRequestIcon.entries.size, glyphs.map { it.name }.toSet().size)
    }
}
