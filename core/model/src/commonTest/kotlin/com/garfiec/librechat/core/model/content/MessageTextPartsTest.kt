package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [flattenSearchableText] mirrors the server's `parseTextParts` (parsers.ts) verbatim. A change
 * here should trace back to an upstream diff, not local preference.
 */
class MessageTextPartsTest {

    private fun text(value: String) = MessageContentPart(type = ContentType.TEXT, text = value)
    private fun think(value: String) = MessageContentPart(type = ContentType.THINK, think = value)
    private fun steer(value: String) = MessageContentPart(type = ContentType.STEER, steer = JsonPrimitive(value))

    @Test
    fun joinsTextPartsWithASpace() {
        assertEquals("Half an answer", listOf(text("Half an"), text("answer")).flattenSearchableText())
    }

    @Test
    fun includesThinkUnlessSkipReasoning() {
        val parts = listOf(think("reasoning"), text("answer"))
        assertEquals("reasoning answer", parts.flattenSearchableText())
        assertEquals("answer", parts.flattenSearchableText(skipReasoning = true))
    }

    @Test
    fun includesSteerOnlyWhenAskedTo() {
        val parts = listOf(text("first"), steer("actually, shorter"), text("second"))
        assertEquals("first second", parts.flattenSearchableText())
        assertEquals("first actually, shorter second", parts.flattenSearchableText(includeSteer = true))
    }

    @Test
    fun nonStringSteerPayloadContributesNothing() {
        val parts = listOf(
            text("first"),
            MessageContentPart(type = ContentType.STEER, steer = buildJsonObject { put("text", "nested") }),
        )
        assertEquals("first", parts.flattenSearchableText(includeSteer = true))
    }

    @Test
    fun spaceRuleComparesALiteralSpaceNotWhitespace() {
        // A chunk ending in "\n" still gets the server's separator space.
        assertEquals("line one\n line two", listOf(text("line one\n"), text("line two")).flattenSearchableText())
        assertEquals("start end", listOf(text("start "), text("end")).flattenSearchableText())
        assertEquals("start end", listOf(text("start"), text(" end")).flattenSearchableText())
    }

    @Test
    fun skipsOtherPartTypesAndEmptyValues() {
        val parts = listOf(
            text("a"),
            MessageContentPart(type = ContentType.TOOL_CALL),
            text(""),
            MessageContentPart(type = ContentType.TEXT, text = null),
            text("b"),
        )
        assertEquals("a b", parts.flattenSearchableText())
    }
}
