package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.wakeup.WakeupDisplay
import com.garfiec.librechat.core.model.wakeup.WakeupKind
import com.garfiec.librechat.core.model.wakeup.WakeupTask
import com.garfiec.librechat.core.model.wakeup.WakeupTaskStatus
import com.garfiec.librechat.feature.chat.util.MessageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SystemEventRowTest {

    private val wakeupText = "A background tool task has finished. Continue using its durable result below.\n" +
        """[{"background_task_id":"bg-1","tool_call_id":"call-1","tool":"web_search","status":"completed","result":"ok"}]"""

    private fun message(text: String, byUser: Boolean) =
        Message(messageId = "m-1", conversationId = "c-1", text = text, isCreatedByUser = byUser)

    @Test
    fun a_user_authored_wakeup_renders_as_a_system_event() {
        assertNotNull(systemEventFor(message(wakeupText, byUser = true), isEditing = false))
    }

    @Test
    fun the_same_text_from_the_assistant_stays_an_ordinary_message() {
        assertNull(systemEventFor(message(wakeupText, byUser = false), isEditing = false))
    }

    @Test
    fun a_wakeup_under_edit_shows_its_text() {
        assertNull(systemEventFor(message(wakeupText, byUser = true), isEditing = true))
    }

    @Test
    fun ordinary_user_text_is_a_user_bubble() {
        assertNull(systemEventFor(message("What did the background task find?", byUser = true), isEditing = false))
    }

    /** The lists read wake-ups from one per-list parse; the edit exception still applies at the read. */
    @Test
    fun the_per_list_parse_agrees_with_systemEventFor() {
        fun node(id: String, text: String, byUser: Boolean) = MessageNode(
            message = Message(messageId = id, conversationId = "c-1", text = text, isCreatedByUser = byUser),
            children = emptyList(),
            siblingIndex = 0,
            siblingCount = 1,
        )
        val wake = node("w", wakeupText, byUser = true)
        val quoted = node("q", wakeupText, byUser = false)
        val plain = node("p", "hello", byUser = true)
        val wakeups = wakeupsByMessageId(listOf(wake, quoted, plain))

        assertEquals(setOf("w"), wakeups.keys)
        assertEquals(systemEventFor(wake.message, isEditing = false), wakeups.wakeupOf(wake, isEditing = false))
        assertNull(wakeups.wakeupOf(wake, isEditing = true))
        assertNull(wakeups.wakeupOf(plain, isEditing = false))
    }

    @Test
    fun the_name_summary_lists_three_distinct_tools_then_a_count() {
        fun tool(name: String) = WakeupTask(taskId = name, status = WakeupTaskStatus.COMPLETED, result = "", toolName = name)
        val display = WakeupDisplay(
            WakeupKind.BACKGROUND_TOOL,
            listOf(tool("a"), tool("b"), tool("a"), tool("c"), tool("d"), tool("e")),
        )
        assertEquals("a, b, c, +2", wakeupNameSummary(display))
    }

    @Test
    fun a_subagent_summary_is_its_type() {
        val display = WakeupDisplay(
            WakeupKind.SUBAGENT,
            listOf(WakeupTask(taskId = "t", status = WakeupTaskStatus.ERROR, result = "", subagentType = "researcher")),
        )
        assertEquals("researcher", wakeupNameSummary(display))
    }
}
