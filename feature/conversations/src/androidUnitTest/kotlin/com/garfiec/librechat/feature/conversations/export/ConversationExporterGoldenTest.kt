package com.garfiec.librechat.feature.conversations.export

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.time.Instant

/**
 * Pins the exported JSON's timestamp bytes across the String→Instant model change. Exports are
 * files on user disks: values must stay ISO-8601 strings (so old app versions can import new
 * exports) and a missing timestamp must stay an explicit `null` (as the pre-Instant exporter
 * emitted). Everything else in the export is free to evolve; these bytes are compatibility surface.
 */
class ConversationExporterGoldenTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()

    private val conversation = Conversation(
        conversationId = "c1",
        title = "Golden",
        // The backend's Mongoose toISOString form; whole seconds normalize to the plain-Z form.
        updatedAt = Instant.parse("2026-03-28T10:00:00.000Z"),
        createdAt = null,
    )

    private fun exporter(dispatcher: CoroutineDispatcher) =
        ConversationExporter(conversationRepository, messageRepository, dispatcher)

    @Test
    fun timestampsExportAsIsoStringAndExplicitNull() = runTest {
        coEvery { conversationRepository.getConversation("c1", null) } returns Result.Success(conversation)
        coEvery { messageRepository.fetchMessagesFull("c1") } returns
            Result.Success(listOf(Message(messageId = "m1", conversationId = "c1", text = "hi")))

        val result = exporter(UnconfinedTestDispatcher(testScheduler)).exportAsJson("c1")

        val encoded = (result as Result.Success).data
        assertThat(encoded).contains("\"updatedAt\": \"2026-03-28T10:00:00Z\"")
        assertThat(encoded).contains("\"createdAt\": null")
    }

    @Test
    fun exportRoundTripsThroughTheImporter() = runTest {
        coEvery { conversationRepository.getConversation("c1", null) } returns Result.Success(conversation)
        coEvery { messageRepository.fetchMessagesFull("c1") } returns
            Result.Success(listOf(Message(messageId = "m1", conversationId = "c1", text = "hi")))
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val encoded = (exporter(dispatcher).exportAsJson("c1") as Result.Success).data
        val imported = ConversationImporter(dispatcher).parseJson(encoded)

        val roundTripped = (imported as Result.Success).data.conversation
        assertThat(roundTripped.updatedAt).isEqualTo(Instant.parse("2026-03-28T10:00:00Z"))
        assertThat(roundTripped.createdAt).isNull()
    }

    /**
     * The server's import route (`getImporter`, api/server/utils/import/importers.js) takes a
     * LibreChat file only with a top-level `conversationId` and `messages`; anything else is
     * "Unsupported import type". `options` is spread into the stored conversation, so it carries
     * settings and never ids, ownership or tags.
     */
    @Test
    fun exportHasTheShapeTheServerImportRecognises() = runTest {
        val agentChat = conversation.copy(
            endpoint = "agents",
            agentId = "agent_1",
            model = "gpt-4o",
            temperature = 0.5,
            user = "user-1",
            tags = listOf("work"),
        )
        coEvery { conversationRepository.getConversation("c1", null) } returns Result.Success(agentChat)
        coEvery { messageRepository.fetchMessagesFull("c1") } returns Result.Success(
            listOf(
                Message(messageId = "m1", conversationId = "c1", text = "hi", isCreatedByUser = true),
                Message(messageId = "m2", conversationId = "c1", parentMessageId = "m1", text = "hello"),
            ),
        )

        val encoded = (exporter(UnconfinedTestDispatcher(testScheduler)).exportAsJson("c1") as Result.Success).data
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertThat(root["conversationId"]?.jsonPrimitive?.content).isEqualTo("c1")
        assertThat(root["endpoint"]?.jsonPrimitive?.content).isEqualTo("agents")
        assertThat(root["title"]?.jsonPrimitive?.content).isEqualTo("Golden")
        assertThat(root["recursive"]?.jsonPrimitive?.boolean).isFalse()
        assertThat(root["messages"]?.jsonArray?.map { it.jsonObject["messageId"]?.jsonPrimitive?.content })
            .containsExactly("m1", "m2").inOrder()
        val options = root["options"]!!.jsonObject
        assertThat(options["agent_id"]?.jsonPrimitive?.content).isEqualTo("agent_1")
        assertThat(options["model"]?.jsonPrimitive?.content).isEqualTo("gpt-4o")
        assertThat(options["temperature"]?.jsonPrimitive?.double).isEqualTo(0.5)
        assertThat(options.keys).containsNoneOf("conversationId", "user", "tags", "createdAt", "updatedAt")
        assertThat(options.values).doesNotContain(JsonNull)
    }

    /** Exports written by pre-Instant app versions must keep importing. */
    @Test
    fun legacyExportWithStringTimestampsImports() = runTest {
        val legacyJson = """
            {
                "conversation": {
                    "conversationId": "c-legacy",
                    "title": "Old",
                    "createdAt": "2026-02-19T10:00:00.000Z",
                    "updatedAt": null
                },
                "messages": [
                    {"messageId": "m1", "conversationId": "c-legacy", "text": "hi"}
                ],
                "exportedAt": 1745000000000,
                "version": 1
            }
        """.trimIndent()

        val imported = ConversationImporter(UnconfinedTestDispatcher(testScheduler)).parseJson(legacyJson)

        val conversation = (imported as Result.Success).data.conversation
        assertThat(conversation.createdAt).isEqualTo(Instant.parse("2026-02-19T10:00:00Z"))
        assertThat(conversation.updatedAt).isNull()
    }
}
