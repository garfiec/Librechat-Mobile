package com.garfiec.librechat.feature.chat.prompts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-character rule [CommandInputTransformation] applies. What the field does with it — the
 * caret, the length limit, input arriving through the IME — is
 * `CommandInputTransformationInstrumentedTest`'s, since only the real input path runs a
 * transformation.
 */
class PromptEditorCommandFieldTest {

    private fun normalize(input: String) = input.mapNotNull(::normalizeCommandChar).joinToString("")

    @Test
    fun aValidCommandIsLeftExactlyAsTyped() {
        listOf("o", "outline", "my-command-2", "-").forEach {
            assertEquals(it, normalize(it))
        }
    }

    @Test
    fun aSlashIsDroppedWhereverItIsTyped() {
        assertEquals("", normalize("/"))
        assertEquals("outline", normalize("//outline"))
        assertEquals("xoutline", normalize("x/outline"))
    }

    @Test
    fun inputIsNormalisedTheWayTheWebFieldDoes() {
        assertEquals("my-command", normalize("My Command"))
        assertEquals("tab-sep", normalize("tab\tsep"))
        assertEquals("summarize", normalize("Summarize!"))
        assertEquals("caf", normalize("café"))
    }
}
