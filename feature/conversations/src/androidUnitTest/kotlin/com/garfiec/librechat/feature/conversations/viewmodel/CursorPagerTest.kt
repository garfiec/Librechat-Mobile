package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CursorPagerTest {

    private data class Call(val cursor: String?, val limit: Int?)

    /** A server holding [total] rows `r0…`, paged 25 at a time by default and capped at 100. */
    private class FakeServer(private val total: Int) {
        val calls = mutableListOf<Call>()
        var gate: CompletableDeferred<Unit>? = null

        suspend fun fetch(cursor: String?, limit: Int?): Result<CursorPage<String>> {
            calls += Call(cursor, limit)
            gate?.await()
            val from = cursor?.toInt() ?: 0
            val to = (from + (limit ?: 25).coerceAtMost(100)).coerceAtMost(total)
            return Result.Success(CursorPage((from until to).map { "r$it" }, to.takeIf { it < total }?.toString()))
        }
    }

    private fun TestScope.pager(server: FakeServer) =
        CursorPager<String>(this, idOf = { it }, onError = {}) { c, l -> server.fetch(c, l) }

    @Test
    fun `a quiet reload past the server's cap refetches every loaded row, in one swap`() = runTest {
        val server = FakeServer(total = 300)
        val pager = pager(server)
        pager.load()
        advanceUntilIdle()
        repeat(5) {
            pager.loadMore()
            advanceUntilIdle()
        }
        assertThat(pager.items.value).hasSize(150)
        server.calls.clear()

        pager.reloadQuietly()
        advanceUntilIdle()

        assertThat(server.calls).containsExactly(Call(null, 100), Call("100", 50)).inOrder()
        assertThat(pager.items.value).hasSize(150)
        assertThat(pager.state.value.nextCursor).isEqualTo("150")
    }

    @Test
    fun `a quiet reload over the first load keeps its spinner`() = runTest {
        val server = FakeServer(total = 10).apply { gate = CompletableDeferred() }
        val pager = pager(server)

        pager.load()
        runCurrent()
        pager.reloadQuietly()
        runCurrent()

        assertThat(pager.state.value.isLoading).isTrue()
        server.gate!!.complete(Unit)
        advanceUntilIdle()
        assertThat(pager.state.value.isLoading).isFalse()
        assertThat(pager.items.value).hasSize(10)
    }

    @Test
    fun `a new load drops a next page that was waiting on a reload`() = runTest {
        val server = FakeServer(total = 300)
        val pager = pager(server)
        pager.load()
        advanceUntilIdle()
        server.gate = CompletableDeferred()

        pager.reloadQuietly()
        runCurrent()
        pager.loadMore()
        pager.load()
        server.gate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(pager.items.value).hasSize(25)
        assertThat(server.calls.filter { it.cursor != null }).isEmpty()
    }
}
