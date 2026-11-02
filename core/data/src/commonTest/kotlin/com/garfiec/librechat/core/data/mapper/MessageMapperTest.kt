package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.WebSearchData
import com.garfiec.librechat.core.model.WebSearchSource
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageMapperTest {

    private fun baseMessage(quotes: List<String>?) = Message(
        messageId = "m1",
        conversationId = "c1",
        text = "hello",
        isCreatedByUser = true,
        quotes = quotes,
    )

    @Test
    fun quotes_roundTripThroughEntity() {
        val quotes = listOf("first excerpt", "second \"quoted\" excerpt")
        val restored = baseMessage(quotes).toEntity().toModel()
        assertEquals(quotes, restored.quotes)
    }

    @Test
    fun isUserSubmitted_roundTripsThroughEntity() {
        assertEquals(true, baseMessage(null).copy(isUserSubmitted = true).toEntity().toModel().isUserSubmitted)
        assertEquals(false, baseMessage(null).copy(isUserSubmitted = false).toEntity().toModel().isUserSubmitted)
        assertNull(baseMessage(null).toEntity().toModel().isUserSubmitted)
    }

    @Test
    fun reasoningOverride_roundTripsThroughEntity_levelAndBudget() {
        val level = ReasoningOverride.level("reasoning_effort", "high")
        val budget = ReasoningOverride("thinkingBudget", JsonPrimitive(4096))
        assertEquals(level, baseMessage(null).copy(reasoningOverride = level).toEntity().toModel().reasoningOverride)
        assertEquals(budget, baseMessage(null).copy(reasoningOverride = budget).toEntity().toModel().reasoningOverride)
        assertNull(baseMessage(null).toEntity().toModel().reasoningOverride)
    }

    @Test
    fun privacyRevision_roundTripsThroughEntity() {
        assertEquals("rev-1", baseMessage(null).copy(privacyRevision = "rev-1").toEntity().toModel().privacyRevision)
        assertNull(baseMessage(null).toEntity().toModel().privacyRevision)
    }

    @Test
    fun quotes_null_staysNull() {
        val restored = baseMessage(null).toEntity().toModel()
        assertNull(restored.quotes)
    }

    @Test
    fun quotes_emptyList_roundTripsAsEmpty() {
        val restored = baseMessage(emptyList()).toEntity().toModel()
        assertEquals(emptyList(), restored.quotes)
    }

    @Test
    fun contentParts_agentAttribution_roundTripsThroughEntity() {
        // Compare Models persistence: the per-part agentId/groupId attribution must
        // survive the Room round-trip, or reopening a comparison can't restore its panes.
        val message = Message(
            messageId = "m1",
            conversationId = "c1",
            content = listOf(
                MessageContentPart(type = ContentType.TEXT, text = "primary", agentId = "agent_a", groupId = 0),
                MessageContentPart(type = ContentType.TEXT, text = "secondary", agentId = "agent_a____1", groupId = 1),
            ),
        )

        val restored = message.toEntity().toModel()

        val parts = restored.content
        assertEquals(2, parts?.size)
        assertEquals("agent_a", parts?.get(0)?.agentId)
        assertEquals(0, parts?.get(0)?.groupId)
        assertEquals("agent_a____1", parts?.get(1)?.agentId)
        assertEquals(1, parts?.get(1)?.groupId)
    }

    @Test
    fun contentParts_toolCallInputValidationError_roundTripsThroughEntity() {
        val message = Message(
            messageId = "m1",
            conversationId = "c1",
            content = listOf(
                MessageContentPart(
                    type = ContentType.TOOL_CALL,
                    toolCall = AgentToolCall(id = "call_1", name = "ask_user_question", inputValidationError = true),
                ),
            ),
        )

        val restored = message.toEntity().toModel()

        assertEquals(true, restored.content?.single()?.toolCall?.inputValidationError)
    }

    @Test
    fun webSearchAttachment_roundTripsTheResultsCitationsIndexInto() {
        // A cache-first open renders from this row: a field dropped here leaves its citations
        // unresolvable until the network refresh lands.
        val search = WebSearchData(
            turn = 1,
            organic = listOf(WebSearchSource(link = "https://a.example", attribution = "A")),
            images = listOf(WebSearchSource(imageUrl = "https://img.example/1.png")),
            videos = listOf(WebSearchSource(link = "https://v.example")),
            references = listOf(WebSearchSource(link = "https://r.example", title = "R")),
        )
        val message = baseMessage(null).copy(attachments = listOf(Attachment(type = "web_search", webSearch = search)))
        assertEquals(search, message.toEntity().toModel().attachments?.single()?.webSearch)
    }

    @Test
    fun searchText_leavesCitationMarkersOut() {
        val indexed = searchTextOf("Claim.\\ue202turn0search0 More \uE200\uE202turn0news1\uE201.", contentJson = null)
        assertEquals("Claim. More.", indexed.text)
    }
}
