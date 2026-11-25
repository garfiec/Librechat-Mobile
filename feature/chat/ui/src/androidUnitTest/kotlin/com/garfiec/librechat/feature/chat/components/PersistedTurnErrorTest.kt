package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.model.error.UserKeyError
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

    /** The error turn `denyRequest` saves when it refuses a send on an existing conversation. */
    @Test
    fun `a saved refusal is classified, not shown as JSON`() {
        val saved = failed("""{"type":"message_limit","max":40,"limiter":"user","windowInMinutes":1}""")

        assertThat(persistedTurnError(saved)).isEqualTo(StreamErrorType.MESSAGE_LIMIT.marker)
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

    @Test
    fun `a key error on an agent names the agent's provider and adds its key`() {
        val keys = UserKeyErrors(
            endpointConfigs = mapOf("google" to EndpointConfig(userProvide = true)),
            agents = listOf(Agent(id = "agent_1", provider = "google")),
            onAddKey = {},
        )

        val shown = savedUserKeyError("""{"type":"no_user_key"}""", endpoint = "agents", model = "agent_1", keys = keys)

        assertThat(shown).isEqualTo(SavedUserKeyError(UserKeyError.NoUserKey("google"), label = "Google", addKeyEndpoint = "google"))
    }

    @Test
    fun `the payload's endpoint outranks the reply's`() {
        val keys = UserKeyErrors(mapOf("anthropic" to EndpointConfig(userProvide = true)), emptyList(), onAddKey = {})
        val raw = """{"type":"expired_user_key","expiredAt":"2026-01-01T00:00:00.000Z","endpoint":"anthropic"}"""

        assertThat(savedUserKeyError(raw, endpoint = "openAI", model = null, keys = keys)?.addKeyEndpoint).isEqualTo("anthropic")
    }

    @Test
    fun `an endpoint whose key the server provides offers no Add key`() {
        val keys = UserKeyErrors(mapOf("openAI" to EndpointConfig(userProvide = false)), emptyList(), onAddKey = {})

        val shown = savedUserKeyError("""{"type":"invalid_user_key"}""", endpoint = "openAI", model = null, keys = keys)

        assertThat(shown?.addKeyEndpoint).isNull()
        assertThat(shown?.label).isEqualTo("OpenAI")
    }

    @Test
    fun `a failure that is not a key error is left to the stream error copy`() {
        assertThat(savedUserKeyError("""{"type":"message_limit","max":40}""", "openAI", null, keys = null)).isNull()
        assertThat(savedUserKeyError("Connection error.", "openAI", null, keys = null)).isNull()
    }
}
