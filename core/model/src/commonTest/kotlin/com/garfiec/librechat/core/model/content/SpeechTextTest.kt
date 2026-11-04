package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechTextTest {

    private fun message(text: String = "", content: List<MessageContentPart>? = null) =
        Message(messageId = "m", conversationId = "c", text = text, content = content)

    @Test
    fun contentPartsSpeakOnlyTheAnswer() {
        val spoken = speechTextOf(
            message(
                content = listOf(
                    MessageContentPart(type = ContentType.THINK, think = "Let me weigh the options."),
                    MessageContentPart(type = ContentType.TEXT, text = "Pick the second one."),
                    MessageContentPart(type = ContentType.TEXT, text = "It is cheaper."),
                ),
            ),
        )

        assertEquals("Pick the second one. It is cheaper.", spoken)
    }

    @Test
    fun aLegacyThinkingBlockInTheTextIsNotSpoken() {
        val spoken = speechTextOf(message(text = ":::thinking\nWeighing it up.\n:::\n\nPick the second one."))

        assertEquals("Pick the second one.", spoken)
    }

    @Test
    fun plainTextIsSpokenAsIs() {
        assertEquals("Just an answer.", speechTextOf(message(text = "  Just an answer.\n")))
    }

    @Test
    fun reasoningAloneSpeaksNothing() {
        val spoken = speechTextOf(
            message(text = "ignored", content = listOf(MessageContentPart(type = ContentType.THINK, think = "Hmm."))),
        )

        assertEquals("", spoken)
    }
}
