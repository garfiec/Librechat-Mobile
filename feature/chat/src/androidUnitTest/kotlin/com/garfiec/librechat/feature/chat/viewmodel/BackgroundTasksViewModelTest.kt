package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BackgroundTaskRepository
import com.garfiec.librechat.core.model.background.BackgroundTaskCancelResponse
import com.garfiec.librechat.core.model.background.BackgroundTaskCancelResult
import com.garfiec.librechat.core.model.background.BackgroundTaskIndex
import com.garfiec.librechat.core.model.background.BackgroundTaskSummary
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundTasksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<BackgroundTaskRepository>()

    private val running =
        BackgroundTaskSummary(taskId = "t1", toolName = "bash_tool", toolCallId = "c", status = "running")
    private val done = running.copy(status = "completed")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.isRuledOutForServer() } returns false
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun index(vararg tasks: BackgroundTaskSummary, cancellable: Boolean = true) =
        Result.Success<BackgroundTaskIndex?>(BackgroundTaskIndex("convo", tasks.toList(), cancellable = cancellable))

    private fun vm() = BackgroundTasksViewModel(repository).apply {
        setActive(true)
    }

    @Test
    fun an_idle_conversation_is_read_once_and_not_polled() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index(done)
        val vm = vm()

        vm.bind("convo", isStreaming = false)
        advanceTimeBy(60_000)
        runCurrent()

        coVerify(exactly = 1) { repository.getTasks("convo") }
        assertThat(vm.uiState.value.isVisible).isTrue()
    }

    @Test
    fun a_running_task_is_polled_until_it_finishes() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returnsMany listOf(index(running), index(running), index(done))
        val vm = vm()

        vm.bind("convo", isStreaming = false)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        coVerify(exactly = 3) { repository.getTasks("convo") }
        assertThat(vm.uiState.value.runningCount).isEqualTo(0)
    }

    @Test
    fun a_settled_run_rereads_the_list() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index()
        val vm = vm()

        vm.bind("convo", isStreaming = true)
        runCurrent()
        vm.bind("convo", isStreaming = false)
        runCurrent()

        coVerify(exactly = 2) { repository.getTasks("convo") }
        assertThat(vm.uiState.value.isVisible).isFalse()
    }

    @Test
    fun a_server_ruled_out_mid_session_is_not_asked_again() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index(running)
        val vm = vm()
        vm.bind("convo", isStreaming = false)
        runCurrent()

        every { repository.isRuledOutForServer() } returns true
        advanceTimeBy(10_000)
        runCurrent()

        coVerify(exactly = 1) { repository.getTasks("convo") }
        assertThat(vm.uiState.value.isVisible).isFalse()
    }

    @Test
    fun stop_is_offered_only_when_the_deployment_allows_cancellation() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index(running, cancellable = false)
        val vm = vm()
        vm.bind("convo", isStreaming = false)
        runCurrent()

        vm.setActive(false)
        assertThat(vm.uiState.value.canStop(running)).isFalse()
    }

    @Test
    fun a_forbidden_stop_withdraws_the_affordance_and_reports_failure() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index(running)
        coEvery { repository.cancel("convo", listOf("t1")) } returns
            Result.Error(ApiException(statusCode = 403, message = "Background task cancellation is not enabled"))
        val vm = vm()
        vm.bind("convo", isStreaming = false)
        runCurrent()

        // The next read reports what the deployment now allows.
        coEvery { repository.getTasks("convo") } returns index(running, cancellable = false)
        vm.stop("t1")
        runCurrent()
        vm.setActive(false)

        assertThat(vm.uiState.value.stopFailed).isTrue()
        assertThat(vm.uiState.value.canStop(running)).isFalse()
    }

    @Test
    fun an_unresolved_cancel_outcome_is_a_failure() = runTest(dispatcher) {
        coEvery { repository.getTasks("convo") } returns index(running)
        coEvery { repository.cancel("convo", null) } returns Result.Success(
            BackgroundTaskCancelResponse(listOf(BackgroundTaskCancelResult("t1", "unavailable"))),
        )
        val vm = vm()
        vm.bind("convo", isStreaming = false)
        runCurrent()

        vm.stopAll()
        runCurrent()
        vm.setActive(false)

        assertThat(vm.uiState.value.stopFailed).isTrue()
        assertThat(vm.uiState.value.stoppingAll).isFalse()
    }
}
