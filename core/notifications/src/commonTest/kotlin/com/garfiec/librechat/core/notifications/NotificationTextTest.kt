package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationTextTest {

    private fun reply(text: String) = Message(messageId = "m", conversationId = "c", text = text)

    @Test
    fun link_percentEncodesTheAccount() {
        assertEquals(
            "librechat://conversation/c1?account=ab12%3Auser%2F42%20x",
            conversationLink("c1", AccountId("ab12:user/42 x")),
        )
    }

    @Test
    fun preview_dropsMarkdownSyntaxAndJoinsLines() {
        val text = "# Plan\n\n- **Pack** the `charger`\n- See [the map](https://example.com)\n\n```kotlin\nval x = 1\n```"
        assertEquals("Plan Pack the charger See the map val x = 1", notificationPreviewOf(reply(text)))
    }

    @Test
    fun preview_leavesOutReasoning() {
        val text = ":::thinking\nworking it out\n:::\nThe answer is 4."
        assertEquals("The answer is 4.", notificationPreviewOf(reply(text)))
    }

    @Test
    fun preview_cutsALongReplyAtAWord() {
        val preview = notificationPreviewOf(reply("word ".repeat(100)))
        assertTrue(preview.length <= PREVIEW_MAX_CHARS + 1, preview)
        assertTrue(preview.endsWith("word…"), preview)
    }

    @Test
    fun preview_isBlankForAReplyWithNoText() {
        assertEquals("", notificationPreviewOf(reply("")))
    }
}
