package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.ContentGroup
import com.garfiec.librechat.feature.chat.util.groupContentParts
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The `skill` tool call: its name parsing, the streaming stand-ins, and parts prepended at index 0. */
class SkillCallTest {

    @Test
    fun theSkillNameIsReadFromObjectOrStringArgs() {
        assertEquals("pdf-tools", parseSkillName(buildJsonObject { put("skillName", "pdf-tools") }))
        // The server stores the prime card's args as a JSON string (`JSON.stringify({ skillName })`).
        assertEquals("pdf-tools", parseSkillName(JsonPrimitive("""{"skillName":"pdf-tools"}""")))
        assertEquals("pdf-tools", parseSkillName("""{"skillName":" pdf-tools "}"""))
    }

    @Test
    fun argsWithoutASkillNameGiveNull() {
        assertNull(parseSkillName(buildJsonObject { put("other", "x") }))
        assertNull(parseSkillName("""{"skillName":""}"""))
        assertNull(parseSkillName("not json"))
        assertNull(parseSkillName(null as String?))
    }

    @Test
    fun theToolNameMatchesExactly() {
        // Upstream routes on `name === 'skill'`; an MCP tool merely containing the word is not one.
        assertTrue(isSkillToolCall("skill"))
        assertFalse(isSkillToolCall("find_skill"))
        assertFalse(isSkillToolCall("skills"))
        assertFalse(isSkillToolCall("Skill"))
        assertFalse(isSkillToolCall(null))
    }

    @Test
    fun everyPickWaitsForItsCardUntilTheStreamDeliversIt() {
        assertEquals(listOf("pdf", "voice"), pendingSkillRows(listOf("pdf", "voice", "pdf", ""), emptyList()))
        // A resync after the run can carry a prepended card; its stand-in goes.
        assertEquals(listOf("voice"), pendingSkillRows(listOf("pdf", "voice"), listOf("pdf")))
        assertEquals(emptyList(), pendingSkillRows(null, emptyList()))
    }

    // ── parts inserted at the front ──────────────────────────────────

    private fun text(value: String) = MessageContentPart(type = ContentType.TEXT, text = value)

    private fun tool(id: String, name: String = "search") =
        MessageContentPart(type = ContentType.TOOL_CALL, toolCall = AgentToolCall(id = id, name = name, output = "done"))

    /** The shape `buildSkillPrimeContentParts` unshifts onto the reply after the run. */
    private fun skillPrime(runId: String, index: Int, name: String) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(
            id = "call_manual_skill_${runId}_$index",
            name = SKILL_TOOL_NAME,
            args = JsonPrimitive("""{"skillName":"$name"}"""),
            output = "Skill \"$name\" loaded. Follow the instructions below.",
        ),
    )

    private fun keyOf(parts: List<MessageContentPart>, toolCallId: String): String =
        groupContentParts(parts).flatMap { it.groups }
            .first { group -> group.entries.any { it.part.toolCall?.id == toolCallId } }
            .key

    @Test
    fun leadingSkillPartsKeepTheReplysOwnPartsKeyedByToolCallNotIndex() {
        val streamed = listOf(text("thinking out loud"), tool("t1"), tool("t2"), text("answer"))
        val final = listOf(skillPrime("r1", 0, "pdf"), skillPrime("r1", 1, "voice")) + streamed

        // The prepended parts shift every index by two; the reply's tool group must keep its
        // identity so its expand state follows it rather than whatever now sits at its index.
        assertEquals(keyOf(streamed, "t1"), keyOf(final, "t1"))

        // The skill cards render first, in the order the server prepended them, each under its
        // own id-based key.
        val groups = groupContentParts(final).flatMap { it.groups }
        val first = groups.first()
        assertEquals(
            listOf("call_manual_skill_r1_0", "call_manual_skill_r1_1"),
            first.entries.map { it.part.toolCall?.id },
        )
        assertEquals("tool:call_manual_skill_r1_0", first.key)
        // Two consecutive tool calls fold into one activity group, as web's groupSequentialToolCalls
        // folds them.
        assertEquals(true, first is ContentGroup.Activity)
        // Every part is still rendered exactly once.
        assertEquals(final.indices.toList(), groups.flatMap { g -> g.entries.map { it.index } }.sorted())
    }

    @Test
    fun aSingleLeadingSkillPartRendersOnItsOwn() {
        val final = listOf(skillPrime("r1", 0, "pdf"), text("answer"))
        val groups = groupContentParts(final).flatMap { it.groups }
        assertEquals(listOf(0, 1), groups.flatMap { g -> g.entries.map { it.index } })
        assertEquals("pdf", parseSkillName(groups.first().entries.first().part.toolCall?.args))
    }
}
