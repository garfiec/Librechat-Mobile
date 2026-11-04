package com.garfiec.librechat.core.model.content

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of upstream `packages/data-provider/src/reasoning.spec.ts`, plus [reasoningDisplay]. */
@OptIn(ExperimentalEncodingApi::class)
class OpaqueReasoningTest {

    private fun bytes(length: Int, seed: Int) = ByteArray(length) { ((it * 37 + seed) % 256).toByte() }

    private val bedrockRedacted = Base64.Default.encode(bytes(1_050, 3))
    private val anthropicRedacted = "EmwKAhgBEgy" + Base64.Default.encode(bytes(600, 5))
    private val openAIEncrypted = "gAAAAABo" + Base64.UrlSafe.encode(bytes(900, 9)).trimEnd('=')
    private val geminiSignature = "CiQB" + Base64.Default.encode(bytes(300, 13))

    private val readable = listOf(
        "The user wants the deploy rolled back. First I will check which revision is live, then compare it with the last healthy one.",
        "Plan:\n1. read `config/app.ts`\n2. patch `getUser()`\n3. run `npm test -- --watch=false`",
        "The docs live at https://www.librechat.ai/docs/configuration/librechat_yaml/object_structure/agents#reasoning-labels and say so.",
        "Short",
        "42",
    )

    private fun chunks(text: String, size: Int) = text.chunked(size)

    private fun stream(deltas: List<String>): OpaqueReasoningStep =
        deltas.fold(OpaqueReasoningStep("", OpaqueReasoningCursor())) { acc, delta ->
            val next = advanceOpaqueReasoning(acc.cursor, delta)
            OpaqueReasoningStep(acc.readable + next.readable, next.cursor)
        }

    @Test
    fun providerPayloadsAreOpaque() {
        listOf(
            bedrockRedacted,
            anthropicRedacted,
            openAIEncrypted,
            geminiSignature,
            bedrockRedacted + Base64.Default.encode(bytes(400, 21)),
            "\n  $bedrockRedacted\n",
        ).forEach { assertTrue(isOpaqueReasoning(it), it.take(40)) }
    }

    @Test
    fun readableTextIsNotOpaqueAndIsKeptAsIs() {
        readable.forEach {
            assertFalse(isOpaqueReasoning(it), it)
            assertEquals(it, stripOpaqueReasoning(it))
        }
    }

    @Test
    fun thePersistedThinkWrapperIsNotContent() {
        assertTrue(isOpaqueReasoning("<think>$bedrockRedacted</think>"))
        assertTrue(isOpaqueReasoning("<think>\n$anthropicRedacted\n</think>"))
        assertFalse(isOpaqueReasoning("<think>Checking the manifest.</think>"))
        assertFalse(isOpaqueReasoning("<think></think>"))
        assertFalse(isOpaqueReasoning(""))
        assertFalse(isOpaqueReasoning(" \n\t"))
    }

    @Test
    fun readableReasoningQuotingAPayloadIsReadable() {
        assertFalse(isOpaqueReasoning("The header carries $openAIEncrypted, so the token is set."))
    }

    @Test
    fun stripKeepsTheProseAroundAPayload() {
        assertEquals(
            "Checking the retry policy.\n\nBackoff fits the burst pattern.",
            stripOpaqueReasoning("Checking the retry policy.\n$bedrockRedacted\nBackoff fits the burst pattern."),
        )
        assertEquals("", stripOpaqueReasoning(anthropicRedacted))
        assertEquals("", stripOpaqueReasoning("<think>$anthropicRedacted</think>"))
    }

    @Test
    fun streamingHoldsOnlyTheUnfinishedWord() {
        val result = stream(listOf("Let", " me", " check", " the", " manifest.", " Then", " roll"))
        assertEquals("Let me check the manifest. Then ", result.readable)
        assertEquals(OpaqueReasoningCursor(tail = "roll", opaque = false), result.cursor)
    }

    @Test
    fun streamingDropsAPayloadWholeOrSplit() {
        assertEquals("", stream(listOf(bedrockRedacted)).readable)
        val split = stream(chunks(bedrockRedacted, 50))
        assertEquals("", split.readable)
        assertTrue(split.cursor.opaque)
    }

    @Test
    fun streamingKeepsProseOnBothSidesOfASplitPayload() {
        val result = stream(
            listOf("Checking the retry policy. ") + chunks(bedrockRedacted, 100) + " Backoff fits the burst pattern.",
        )
        assertEquals("Checking the retry policy.  Backoff fits the burst pattern.", result.readable)
        assertEquals(OpaqueReasoningCursor(), result.cursor)
    }

    @Test
    fun streamingNeverReleasesThePayloadsEnd() {
        val payload = chunks(bedrockRedacted, 100)
        val result = stream(listOf("Checking. ") + payload.dropLast(1) + "${payload.last()}. Rolling back.")
        assertEquals("Checking. . Rolling back.", result.readable)
    }

    @Test
    fun readableReasoningSettledAndStreaming() {
        val token = "a".repeat(60)
        assertEquals("Done.\n", readableReasoning("Done.\n$bedrockRedacted"))
        assertEquals("Hash $token", readableReasoning("Hash $token"))
        val prefix = bedrockRedacted.take(100)
        assertEquals("Checking. ", readableReasoning("Checking. $prefix", streaming = true))
        assertEquals("", readableReasoning(prefix, streaming = true))
        assertEquals("Checking the manif", readableReasoning("Checking the manif", streaming = true))
    }

    @Test
    fun flushReleasesOnlyAWordSizedTail() {
        assertEquals("manifest", flushOpaqueReasoning(OpaqueReasoningCursor(tail = "manifest")))
        assertEquals("", flushOpaqueReasoning(OpaqueReasoningCursor(tail = "a".repeat(40))))
        assertEquals("", flushOpaqueReasoning(OpaqueReasoningCursor(opaque = true)))
    }

    @Test
    fun displayMarksOnlyUnreadableReasoningEncrypted() {
        assertEquals(ReasoningDisplay("", encrypted = true), reasoningDisplay(bedrockRedacted))
        assertEquals(ReasoningDisplay("Checking.", encrypted = false), reasoningDisplay("Checking."))
        assertEquals(ReasoningDisplay("Done.\n", encrypted = false), reasoningDisplay("Done.\n$bedrockRedacted"))
        // Live: a payload already classified is encrypted; a run still too short to call is just held.
        assertTrue(reasoningDisplay(bedrockRedacted, streaming = true).encrypted)
        assertEquals(ReasoningDisplay("", encrypted = false), reasoningDisplay(bedrockRedacted.take(100), streaming = true))
    }
}
