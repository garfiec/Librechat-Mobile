package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A turn the server saved as failed carries the failure as its text, never as content. */
class PersistedTurnErrorTest {

    private fun failed(text: String) = Message(messageId = "m-1", conversationId = "c-1", text = text, error = true)

    @Test
    fun `a typed initialization failure is classified, not shown as JSON`() {
        val saved = failed("""{"code":"code_workspace_unavailable","message":"workspace down"}""")

        assertThat(persistedTurnError(saved)).isEqualTo(StreamErrorType.CODE_WORKSPACE_UNAVAILABLE.marker)
    }

    @Test
    fun `an untyped failure keeps the server's own words`() {
        assertThat(persistedTurnError(failed("Connection error."))).isEqualTo("Connection error.")
    }

    @Test
    fun `an ordinary message is not an error`() {
        assertThat(persistedTurnError(Message(messageId = "m-1", conversationId = "c-1", text = """{"code":"x"}"""))).isNull()
    }

    /** A failure the server recorded as content renders through the content part instead. */
    @Test
    fun `a failed turn with content parts is left to them`() {
        val saved = failed("boom").copy(content = listOf(MessageContentPart(type = ContentType.ERROR, error = "boom")))

        assertThat(persistedTurnError(saved)).isNull()
    }
}
