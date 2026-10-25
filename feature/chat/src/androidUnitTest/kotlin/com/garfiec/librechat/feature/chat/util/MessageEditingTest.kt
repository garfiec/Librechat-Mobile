package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The editor's decisions, as pure functions.
 *
 * Both the affordance (`MessageList` hides Edit, disables Save, withholds Rerun) and the sink
 * (`MessageEditingDelegate` refuses the same things) read these, so they are the one place the two
 * can be kept from disagreeing — a button that is offered must be one the delegate will honour.
 */
class MessageEditingTest {

    private fun text(text: String) = MessageContentPart(type = ContentType.TEXT, text = text)
    private fun think(text: String) = MessageContentPart(type = ContentType.THINK, think = text)
    private fun tool() = MessageContentPart(type = ContentType.TOOL_CALL)

    private fun reply(vararg parts: MessageContentPart, flat: String = "") = Message(
        messageId = "a1",
        conversationId = "c1",
        parentMessageId = "u1",
        text = flat,
        content = parts.toList().ifEmpty { null },
    )

    // ── Which editor opens ───────────────────────────────────────────

    @Test
    fun `a message with no content parts is edited as flat text`() {
        assertThat(editShapeOf(reply(flat = "an answer"))).isEqualTo(MessageEditShape.PLAIN_TEXT)
    }

    @Test
    fun `an empty content list is edited as flat text`() {
        // Matches what the renderer does: an empty list falls back to the flat text.
        val message = reply(flat = "an answer").copy(content = emptyList())
        assertThat(editShapeOf(message)).isEqualTo(MessageEditShape.PLAIN_TEXT)
    }

    @Test
    fun `a reply with prose is edited part by part`() {
        assertThat(editShapeOf(reply(think("t"), tool(), text("a")))).isEqualTo(MessageEditShape.CONTENT_PARTS)
    }

    @Test
    fun `a reply of tool calls alone has no editor`() {
        assertThat(editShapeOf(reply(tool(), tool()))).isEqualTo(MessageEditShape.NONE)
        assertThat(hasEditablePart(reply(tool()))).isFalse()
    }

    // ── Which parts get a field ──────────────────────────────────────

    @Test
    fun `prose and reasoning are editable, other types are not`() {
        assertThat(isEditablePart(text("a"))).isTrue()
        assertThat(isEditablePart(think("t"))).isTrue()
        assertThat(isEditablePart(tool())).isFalse()
        assertThat(isEditablePart(MessageContentPart(type = ContentType.IMAGE_URL))).isFalse()
        assertThat(isEditablePart(null)).isFalse()
    }

    @Test
    fun `a text part that is really a tool-call container is not editable`() {
        val container = MessageContentPart(type = ContentType.TEXT, text = "a", toolCallIds = listOf("call_1"))
        assertThat(isEditablePart(container)).isFalse()
    }

    @Test
    fun `a text part carrying an empty tool-call list is still prose`() {
        // An empty array names no tool calls. A DELIBERATE divergence: upstream's isEditablePart
        // tests `tool_call_ids != null` and would hide the field for this part.
        val empty = MessageContentPart(type = ContentType.TEXT, text = "a", toolCallIds = emptyList())
        assertThat(isEditablePart(empty)).isTrue()
    }

    @Test
    fun `artifact-bearing prose keeps its artifact card instead of becoming a field`() {
        val artifact = text(":::artifact{identifier=\"x\" type=\"text/html\"}\n<p>hi</p>\n:::")
        assertThat(isEditablePart(artifact)).isFalse()
    }

    @Test
    fun `seeds are keyed by raw content index so a skipped part does not shift the rest`() {
        val seeds = partEditSeeds(reply(think("t"), tool(), text("a")))
        assertThat(seeds).containsExactly(0, "t", 2, "a")
    }

    @Test
    fun `a field shows the value its own type names`() {
        assertThat(partEditText(think("t"))).isEqualTo("t")
        assertThat(partEditText(text("a"))).isEqualTo("a")
        // A think part with a stray `text` must not show it: the save would write it to `think`.
        val mixed = MessageContentPart(type = ContentType.THINK, think = "t", text = "a")
        assertThat(partEditText(mixed)).isEqualTo("t")
    }

    // ── Changes ──────────────────────────────────────────────────────

    @Test
    fun `only drafts differing from their baseline count as changed`() {
        val originals = mapOf(0 to "a", 2 to "b")
        assertThat(changedPartIndices(mapOf(0 to "a", 2 to "b"), originals)).isEmpty()
        assertThat(changedPartIndices(mapOf(0 to "a!", 2 to "b"), originals)).containsExactly(0)
        // In content order, which is the order the sequential save walks.
        assertThat(changedPartIndices(mapOf(0 to "a!", 2 to "b!"), originals)).containsExactly(0, 2).inOrder()
    }

    @Test
    fun `an emptied change is blank but an already-empty part left alone is not`() {
        assertThat(hasBlankPartEdit(mapOf(0 to "   "), mapOf(0 to "a"))).isTrue()
        assertThat(hasBlankPartEdit(mapOf(0 to ""), mapOf(0 to ""))).isFalse()
        assertThat(hasBlankPartEdit(mapOf(0 to "a"), mapOf(0 to "a"))).isFalse()
    }

    // ── What a rerun may carry ───────────────────────────────────────

    @Test
    fun `exactly one changed prose part may ride the rerun`() {
        val message = reply(think("t"), text("a"))
        val originals = mapOf(0 to "t", 1 to "a")
        assertThat(resubmittablePartIndex(message, mapOf(0 to "t", 1 to "a!"), originals)).isEqualTo(1)
    }

    @Test
    fun `no change carries nothing, which the caller reads as a plain regenerate`() {
        val message = reply(text("a"))
        assertThat(resubmittablePartIndex(message, mapOf(0 to "a"), mapOf(0 to "a"))).isNull()
    }

    @Test
    fun `two changed parts carry nothing because the field holds one`() {
        val message = reply(text("a"), text("b"))
        val originals = mapOf(0 to "a", 1 to "b")
        assertThat(resubmittablePartIndex(message, mapOf(0 to "a!", 1 to "b!"), originals)).isNull()
    }

    @Test
    fun `a changed reasoning part carries nothing`() {
        // Servers at v0.8.4 and v0.8.7 read `text` out of editedContent whatever `type` says, so a
        // think payload writes undefined over the reasoning prefix. Save handles it instead.
        val message = reply(think("t"), text("a"))
        val originals = mapOf(0 to "t", 1 to "a")
        assertThat(resubmittablePartIndex(message, mapOf(0 to "t!", 1 to "a"), originals)).isNull()
    }

    // ── The turn a rerun replays ─────────────────────────────────────

    @Test
    fun `the rerun parent is the user turn above the reply`() {
        val user = Message(messageId = "u1", conversationId = "c1", text = "ask", isCreatedByUser = true)
        assertThat(findRerunParent(listOf(user), "u1")).isEqualTo(user)
    }

    @Test
    fun `a reply chained onto another reply has no rerun parent`() {
        // An imported thread whose empty human message was skipped.
        val earlier = Message(messageId = "a0", conversationId = "c1", text = "first")
        assertThat(findRerunParent(listOf(earlier), "a0")).isNull()
    }

    @Test
    fun `a reply left at the root has no rerun parent`() {
        assertThat(findRerunParent(emptyList(), null)).isNull()
        assertThat(findRerunParent(emptyList(), "missing")).isNull()
    }
}
