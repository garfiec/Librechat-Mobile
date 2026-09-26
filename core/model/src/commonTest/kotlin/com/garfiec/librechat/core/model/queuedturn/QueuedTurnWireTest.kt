package com.garfiec.librechat.core.model.queuedturn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class QueuedTurnWireTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    /** The shape `POST /api/agents/chat/queued-turns` answers 202 with. */
    private val enqueued = """
        {
          "receipt": {
            "conversationId": "convo-1",
            "parentMessageId": "msg-1",
            "clientRequestId": "req-1",
            "text": "and then summarise it",
            "files": [{"file_id": "f-1", "type": "image/png", "llmDeliveryPath": "provider"}],
            "quotes": ["the second paragraph"],
            "manualSkills": ["web_search"],
            "priority": false,
            "expectedPredecessorCreatedAt": 1758000000000,
            "queuedTurnId": "qt-1",
            "status": "queued",
            "position": 1,
            "revision": 7,
            "createdAt": "2026-09-18T10:00:00.000Z",
            "updatedAt": "2026-09-18T10:00:00.000Z"
          },
          "capability": { "supported": true, "durability": "durable" }
        }
    """.trimIndent()

    @Test
    fun decodes_an_enqueue_receipt() {
        val response = json.decodeFromString<EnqueueQueuedTurnResponse>(enqueued)
        val receipt = requireNotNull(response.receipt)

        assertEquals("qt-1", receipt.queuedTurnId)
        assertEquals("req-1", receipt.clientRequestId)
        assertEquals(QueuedTurnStatus.QUEUED, receipt.status)
        assertEquals(1, receipt.position)
        assertEquals(7L, receipt.revision)
        assertEquals(1758000000000L, receipt.expectedPredecessorCreatedAt)
        assertEquals(listOf("the second paragraph"), receipt.quotes)
        assertEquals(listOf("web_search"), receipt.manualSkills)
        assertEquals("f-1", receipt.files?.single()?.fileId)
        assertTrue(receipt.isUnsettled)
        assertTrue(response.capability.supported)
    }

    @Test
    fun an_admitted_receipt_carries_the_boundary_it_actually_consumed() {
        // `effectivePredecessorCreatedAt` advances past the captured `expected…` as turns chain,
        // so reading one for the other picks up a stale boundary.
        val body = """
            {
              "queuedTurnId": "qt-2", "clientRequestId": "req-2", "conversationId": "convo-1",
              "parentMessageId": "msg-1", "text": "next", "status": "admitted", "revision": 8,
              "expectedPredecessorCreatedAt": 1758000000000,
              "effectivePredecessorCreatedAt": 1758000009999,
              "createdAt": "2026-09-18T10:00:01.000Z", "updatedAt": "2026-09-18T10:00:02.000Z"
            }
        """.trimIndent()

        val receipt = json.decodeFromString<AgentQueuedTurnReceipt>(body)

        assertEquals(QueuedTurnStatus.ADMITTED, receipt.status)
        assertEquals(1758000009999L, receipt.effectivePredecessorCreatedAt)
        assertTrue(isQueuedTurnSuccessorOwed(listOf(receipt)))
    }

    @Test
    fun a_root_admission_says_so_explicitly() {
        val body = """
            {
              "queuedTurnId": "qt-3", "clientRequestId": "req-3", "text": "first",
              "status": "admitted", "revision": 1, "rootPredecessor": true,
              "createdAt": "2026-09-18T10:00:00.000Z", "updatedAt": "2026-09-18T10:00:00.000Z"
            }
        """.trimIndent()

        val receipt = json.decodeFromString<AgentQueuedTurnReceipt>(body)

        assertEquals(true, receipt.rootPredecessor)
        assertNull(receipt.effectivePredecessorCreatedAt)
    }

    @Test
    fun an_unknown_status_degrades_the_row_instead_of_failing_the_list() {
        // The reason `status` is a String and not an enum: a value added by a later server would
        // throw at decode and take the whole projection with it, including the rows that matter.
        val body = """
            {
              "queuedTurns": [
                {"queuedTurnId": "qt-4", "clientRequestId": "req-4", "text": "a",
                 "status": "some_future_state", "revision": 1,
                 "createdAt": "2026-09-18T10:00:00.000Z", "updatedAt": "2026-09-18T10:00:00.000Z"},
                {"queuedTurnId": "qt-5", "clientRequestId": "req-5", "text": "b",
                 "status": "queued", "revision": 2,
                 "createdAt": "2026-09-18T10:00:00.000Z", "updatedAt": "2026-09-18T10:00:00.000Z"}
              ],
              "capability": {"supported": true, "durability": "durable"},
              "revision": 2
            }
        """.trimIndent()

        val response = json.decodeFromString<ListQueuedTurnsResponse>(body)

        assertEquals(2, response.queuedTurns.size)
        assertTrue(shouldPollQueuedTurns(response.queuedTurns))
    }

    @Test
    fun a_refusing_server_decodes_its_capability_without_a_durability() {
        // Upstream models this as a discriminated union, so the `false` arm carries no
        // `durability` at all. A required field here would fail to decode the refusal.
        val response = json.decodeFromString<ListQueuedTurnsResponse>(
            """{"queuedTurns": [], "capability": {"supported": false}, "revision": 0}""",
        )

        assertEquals(false, response.capability.supported)
        assertNull(response.capability.durability)
    }

    @Test
    fun a_quarantined_admission_decodes_its_failure() {
        val body = """
            {
              "queuedTurnId": "qt-6", "clientRequestId": "req-6", "text": "c",
              "status": "claimed", "revision": 3,
              "failure": {"code": "ADMISSION_INDETERMINATE", "message": "could not confirm"},
              "createdAt": "2026-09-18T10:00:00.000Z", "updatedAt": "2026-09-18T10:00:00.000Z"
            }
        """.trimIndent()

        val receipt = json.decodeFromString<AgentQueuedTurnReceipt>(body)

        assertTrue(receipt.isAdmissionIndeterminate)
        assertTrue(receipt.isUnsettled)
    }

    @Test
    fun an_enqueue_body_omits_what_was_not_set() {
        // `encodeDefaults = false` plus nullable optionals: the server's zod schema rejects a
        // `null` where it expects an absent key, so a request must not send one.
        val encoded = json.encodeToString(
            EnqueueQueuedTurnRequest(
                conversationId = "convo-1",
                parentMessageId = "msg-1",
                clientRequestId = "req-1",
                text = "hello",
            ),
        )

        assertEquals(
            """{"conversationId":"convo-1","parentMessageId":"msg-1",""" +
                """"clientRequestId":"req-1","text":"hello"}""",
            encoded,
        )
    }
}
