package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.result.Result
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SynthesizeInSegmentsTest {

    private val longReply = "A sentence that runs a while. ".repeat(400)

    @Test
    fun requestsEverySegmentInOrderAndFlagsOnlyTheLast() {
        val requested = mutableListOf<String>()
        val handed = mutableListOf<Pair<String, Boolean>>()

        val failure = runSuspend {
            synthesizeInSegments(
                text = longReply,
                synthesize = { requested += it; Result.Success(it.length) },
                onSegment = { segment, _, isLast -> handed += segment to isLast; true },
            )
        }

        assertNull(failure)
        assertTrue(requested.size > 1)
        assertEquals(splitForSpeech(longReply, SERVER_TTS_SEGMENT_CHARS), requested)
        assertEquals(requested, handed.map { it.first })
        assertEquals(listOf(requested.lastIndex), handed.indices.filter { handed[it].second })
        assertEquals(longReply, requested.joinToString(""))
    }

    @Test
    fun theFirstFailedRequestEndsTheReadAndIsReturned() {
        val error = Result.Error(message = "quota")
        var calls = 0
        val handed = mutableListOf<String>()

        val failure = runSuspend {
            synthesizeInSegments(
                text = longReply,
                synthesize = { calls++; if (calls == 2) error else Result.Success(Unit) },
                onSegment = { segment, _, _ -> handed += segment; true },
            )
        }

        assertSame(error, failure)
        assertEquals(2, calls)
        assertEquals(1, handed.size)
    }

    @Test
    fun aShortReplyIsOneLastSegment() {
        val handed = mutableListOf<Pair<String, Boolean>>()

        runSuspend {
            synthesizeInSegments(
                text = "Hello.",
                synthesize = { Result.Success(Unit) },
                onSegment = { segment, _, isLast -> handed += segment to isLast; true },
            )
        }

        assertEquals(listOf("Hello." to true), handed)
    }

    @Test
    fun aConsumerThatDeclinesStopsFurtherRequests() {
        var calls = 0

        val failure = runSuspend {
            synthesizeInSegments(
                text = longReply,
                synthesize = { calls++; Result.Success(Unit) },
                onSegment = { _, _, _ -> false },
            )
        }

        assertNull(failure)
        assertEquals(1, calls)
    }

    /** Runs a block whose fakes never suspend, without a coroutines-test dependency in commonTest. */
    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: kotlin.Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return checkNotNull(outcome) { "block suspended" }.getOrThrow()
    }
}
