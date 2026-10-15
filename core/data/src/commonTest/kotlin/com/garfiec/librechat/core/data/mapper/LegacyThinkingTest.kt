package com.garfiec.librechat.core.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LegacyThinkingTest {

    private val legacy = ":::thinking\nLet me think about RSA.\n:::\n# RSA keys\n\nNo, hashcat can't."

    @Test
    fun aThinkingBlockInTheTextIsIndexedAsReasoning() {
        val indexed = searchTextOf(legacy, contentJson = null)

        val text = assertNotNull(indexed.text)
        val visibleLength = assertNotNull(indexed.visibleLength)
        assertEquals("# RSA keys\n\nNo, hashcat can't.", visibleTextOf(text, visibleLength))
        assertEquals("Let me think about RSA.", text.substring(visibleLength).trim())
    }

    @Test
    fun theSplitMatchesTheWebClients() {
        val split = assertNotNull(LegacyThinking.split(legacy))

        assertEquals("Let me think about RSA.", split.reasoning)
        assertEquals("# RSA keys\n\nNo, hashcat can't.", split.answer)
    }

    @Test
    fun textWithoutABlockIsLeftAlone() {
        assertNull(LegacyThinking.split("just an answer"))
        assertNull(searchTextOf("just an answer", contentJson = null).visibleLength)
    }
}
