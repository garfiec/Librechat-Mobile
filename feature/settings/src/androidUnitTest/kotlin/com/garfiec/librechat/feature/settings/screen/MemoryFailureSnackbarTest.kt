package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesEvent
import com.garfiec.librechat.feature.settings.viewmodel.MemoryFailure
import com.garfiec.librechat.feature.settings.viewmodel.MemoryOperation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryFailureSnackbarTest {

    private val host = SnackbarHostState()
    private val events = Channel<MemoriesEvent>(Channel.BUFFERED)
    private val retried = mutableListOf<MemoryOperation>()

    private fun TestScope.collect() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            showMemoryFailures(
                events = events.receiveAsFlow(),
                snackbarHostState = host,
                retryLabel = "Retry",
                onRetry = { retried += it },
                message = { it.operation::class.simpleName.orEmpty() },
            )
        }
    }

    @Test
    fun `a retryable failure times out instead of staying on screen`() = runTest {
        collect()

        events.send(MemoriesEvent.Failed(MemoryOperation.Refresh, MemoryFailure.Network))

        val shown = host.currentSnackbarData!!.visuals
        assertThat(shown.actionLabel).isEqualTo("Retry")
        assertThat(shown.duration).isEqualTo(SnackbarDuration.Long)
    }

    @Test
    fun `a newer failure replaces the one on screen`() = runTest {
        collect()
        val memory = Memory(key = "k", value = "v")

        events.send(MemoriesEvent.Failed(MemoryOperation.Refresh, MemoryFailure.Network))
        events.send(MemoriesEvent.Failed(MemoryOperation.Delete(memory), MemoryFailure.Generic))

        assertThat(host.currentSnackbarData!!.visuals.message).isEqualTo("Delete")
    }

    @Test
    fun `Retry re-runs the shown failure's operation`() = runTest {
        collect()
        val memory = Memory(key = "k", value = "v")

        events.send(MemoriesEvent.Failed(MemoryOperation.Delete(memory), MemoryFailure.Network))
        host.currentSnackbarData!!.performAction()

        assertThat(retried).containsExactly(MemoryOperation.Delete(memory))
    }
}
