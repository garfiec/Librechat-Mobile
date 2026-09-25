package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.model.trace.TraceErrorCode
import com.garfiec.librechat.core.model.trace.TracePage
import com.garfiec.librechat.core.model.trace.TraceRecord
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import com.garfiec.librechat.core.model.trace.TraceStatus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The paging seam, which is where this surface can be wrong in ways one page never reveals.
 *
 * Three things only a second page can break: a turn that spans the boundary, the `sourceId` a
 * detail read has to carry from the page that listed its record, and what a failed older read is
 * allowed to discard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TraceViewerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<TraceRepository>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TraceViewerViewModel(repository)

    private fun record(id: String, messageId: String, startTime: String) =
        TraceRecord(id = id, messageId = messageId, startTime = startTime)

    @Test
    fun `a turn spanning two pages renders as one turn`() = runTest {
        coEvery { repository.getRecords("c1", null) } returns Result.Success(
            TracePage(records = listOf(record("r2", "m1", "2026-09-18T09:00:01Z")), nextCursor = "c"),
        )
        coEvery { repository.getRecords("c1", "c") } returns Result.Success(
            TracePage(records = listOf(record("r1", "m1", "2026-09-18T09:00:00Z"))),
        )

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.loadOlder()

        val turns = viewModel.uiState.value.turns
        assertThat(turns).hasSize(1)
        assertThat(turns.single().records.map { it.id }).containsExactly("r1", "r2").inOrder()
    }

    @Test
    fun `a detail read carries the sourceId of the page that listed the record`() = runTest {
        // A multi-project deployment serves records from different sources across pages, so a
        // detail fetched with the wrong one reads the wrong project — or nothing.
        coEvery { repository.getRecords("c1", null) } returns Result.Success(
            TracePage(
                records = listOf(record("r2", "m2", "2026-09-18T10:00:00Z")),
                nextCursor = "c",
                sourceId = "project-a",
            ),
        )
        coEvery { repository.getRecords("c1", "c") } returns Result.Success(
            TracePage(
                records = listOf(record("r1", "m1", "2026-09-18T09:00:00Z")),
                sourceId = "project-b",
            ),
        )
        coEvery { repository.getRecord(any(), any(), any(), any()) } returns
            Result.Success(TraceRecordDetail(record = record("r1", "m1", "")))

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.loadOlder()
        viewModel.select(record("r1", "m1", "2026-09-18T09:00:00Z"))

        coVerify(exactly = 1) {
            repository.getRecord("c1", "r1", "m1", "project-b")
        }
    }

    /**
     * The detail route is rate limited and a finished record's detail cannot change, so re-reading
     * it every time the user taps back and forth spends the budget on an answer already held.
     */
    @Test
    fun `a settled record's detail is read once however often it is reopened`() = runTest {
        val settled = record("r1", "m1", "2026-09-18T09:00:00Z").copy(status = TraceStatus.OK)
        coEvery { repository.getRecords("c1", null) } returns
            Result.Success(TracePage(records = listOf(settled)))
        coEvery { repository.getRecord(any(), any(), any(), any()) } returns
            Result.Success(TraceRecordDetail(record = settled))

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.select(settled)
        viewModel.closeDetail()
        viewModel.select(settled)

        assertThat(viewModel.uiState.value.selectedDetail).isNotNull()
        coVerify(exactly = 1) { repository.getRecord("c1", "r1", "m1", any()) }
    }

    /** A record still running has more to say, so its detail is never served from the cache. */
    @Test
    fun `a running record's detail is re-read every time`() = runTest {
        val running = record("r1", "m1", "2026-09-18T09:00:00Z").copy(status = TraceStatus.RUNNING)
        coEvery { repository.getRecords("c1", null) } returns
            Result.Success(TracePage(records = listOf(running)))
        coEvery { repository.getRecord(any(), any(), any(), any()) } returns
            Result.Success(TraceRecordDetail(record = running))

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.select(running)
        viewModel.closeDetail()
        viewModel.select(running)

        coVerify(exactly = 2) { repository.getRecord("c1", "r1", "m1", any()) }
    }

    /** A refresh drops the pages; the details read against them go with it. */
    @Test
    fun `a refresh clears the cached details`() = runTest {
        val settled = record("r1", "m1", "2026-09-18T09:00:00Z").copy(status = TraceStatus.OK)
        coEvery { repository.getRecords("c1", null) } returns
            Result.Success(TracePage(records = listOf(settled)))
        coEvery { repository.getRecord(any(), any(), any(), any()) } returns
            Result.Success(TraceRecordDetail(record = settled))

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.select(settled)
        viewModel.refresh()
        viewModel.select(settled)

        coVerify(exactly = 2) { repository.getRecord("c1", "r1", "m1", any()) }
    }

    @Test
    fun `a failed older read keeps what is already loaded and retries from the same cursor`() =
        runTest {
            coEvery { repository.getRecords("c1", null) } returns Result.Success(
                TracePage(records = listOf(record("r2", "m2", "2026-09-18T10:00:00Z")), nextCursor = "c"),
            )
            coEvery { repository.getRecords("c1", "c") } returns Result.Error(message = "boom")

            val viewModel = viewModel()
            viewModel.openFor("c1")
            viewModel.loadOlder()

            // Starting over would throw away the page the user is looking at over a transient fault.
            assertThat(viewModel.uiState.value.turns).hasSize(1)
            assertThat(viewModel.uiState.value.failedOverResults).isTrue()

            viewModel.retry()
            coVerify(exactly = 2) { repository.getRecords("c1", "c") }
            coVerify(exactly = 1) { repository.getRecords("c1", null) }
        }

    @Test
    fun `a changed trace can only be retried from the newest page`() = runTest {
        // `invalid_request` means the cursor no longer matches the trace, so there is nothing to
        // resume from — re-asking the same cursor would fail identically, forever.
        coEvery { repository.getRecords("c1", null) } returns Result.Success(
            TracePage(records = listOf(record("r2", "m2", "2026-09-18T10:00:00Z")), nextCursor = "c"),
        )
        coEvery { repository.getRecords("c1", "c") } returns Result.Error(
            exception = ApiException(
                statusCode = 400,
                message = "Invalid trace request",
                body = """{"error":"Invalid trace request","errorCode":"invalid_request"}""",
            ),
            message = "Invalid trace request",
        )

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.loadOlder()

        // The seam this pins: the code has to survive the trip from the response body to state,
        // through ApiException. A message-only Result would leave every branch below unreachable.
        assertThat(viewModel.uiState.value.errorCode).isEqualTo(TraceErrorCode.INVALID_REQUEST)

        viewModel.retry()
        coVerify(exactly = 2) { repository.getRecords("c1", null) }
    }

    @Test
    fun `a refresh drops the pages loaded beneath the newest`() = runTest {
        // Replaying every older page through the per-user trace limiter on each refresh would
        // exhaust it; they reload on demand.
        coEvery { repository.getRecords("c1", null) } returns Result.Success(
            TracePage(records = listOf(record("r2", "m2", "2026-09-18T10:00:00Z")), nextCursor = "c"),
        )
        coEvery { repository.getRecords("c1", "c") } returns Result.Success(
            TracePage(records = listOf(record("r1", "m1", "2026-09-18T09:00:00Z"))),
        )

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.loadOlder()
        assertThat(viewModel.uiState.value.turns).hasSize(2)

        viewModel.refresh()

        assertThat(viewModel.uiState.value.turns).hasSize(1)
        coVerify(exactly = 1) { repository.getRecords("c1", "c") }
    }

    @Test
    fun `a read still in flight when another conversation opens does not land in it`() = runTest {
        // The switch itself is not enough to break this — a read that had already returned is
        // simply discarded with the pages. What breaks it is one still in flight, which without
        // the epoch appends its page to the conversation that replaced it.
        val firstRead = CompletableDeferred<Unit>()
        coEvery { repository.getRecords("c1", null) } coAnswers {
            firstRead.await()
            Result.Success(TracePage(records = listOf(record("r1", "m1", "2026-09-18T09:00:00Z"))))
        }
        coEvery { repository.getRecords("c2", null) } returns Result.Success(
            TracePage(records = listOf(record("r9", "m9", "2026-09-18T11:00:00Z"))),
        )

        val viewModel = viewModel()
        viewModel.openFor("c1")
        viewModel.openFor("c2")
        firstRead.complete(Unit)

        assertThat(viewModel.uiState.value.turns.flatMap { it.records }.map { it.id })
            .containsExactly("r9")
    }
}
