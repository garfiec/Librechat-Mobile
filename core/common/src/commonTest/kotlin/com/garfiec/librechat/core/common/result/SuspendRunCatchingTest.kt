package com.garfiec.librechat.core.common.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SuspendRunCatchingTest {

    @Test
    fun capturesSuccess() = runTest {
        assertEquals("hello", suspendRunCatching { "hello" }.getOrNull())
    }

    @Test
    fun capturesFailure() = runTest {
        val result = suspendRunCatching<String> { throw IllegalStateException("boom") }
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            suspendRunCatching<String> { throw CancellationException("cancelled") }
        }
    }
}
