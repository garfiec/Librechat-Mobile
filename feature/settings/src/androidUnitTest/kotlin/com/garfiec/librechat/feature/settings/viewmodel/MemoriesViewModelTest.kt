package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The page: loading, refresh, search, partition chips, role gates and the opt-out switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest : MemoriesViewModelTestBase() {

    // ------------------------------------------------------------------------------- first load

    @Test
    fun `the first load shows the spinner state until it settles`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))

        val viewModel = createViewModel()
        assertThat(viewModel.state.isFirstLoad).isTrue()
        advanceUntilIdle()

        assertThat(viewModel.state.isFirstLoad).isFalse()
        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_tea")
        assertThat(viewModel.state.loadError).isNull()
    }

    @Test
    fun `a failed first load is the banner, not an event`() = runTest {
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        assertThat(viewModel.state.isFirstLoad).isFalse()
        assertThat(viewModel.state.loadError).isEqualTo(MemoryFailure.Network)
        assertThat(events).isEmpty()
    }

    @Test
    fun `retrying the first load clears the banner and loads`() = runTest {
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { gate.await() }
        viewModel.retryLoad()
        advanceUntilIdle()
        assertThat(viewModel.state.isFirstLoad).isTrue()
        assertThat(viewModel.state.loadError).isNull()

        gate.complete(page(memory("likes_tea")))
        advanceUntilIdle()

        assertThat(viewModel.state.loadError).isNull()
        assertThat(viewModel.state.memories).hasSize(1)
    }

    @Test
    fun `a reload after the list is empty never brings the spinner back`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.memories).isEmpty()

        val gate = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { gate.await() }
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.state.isFirstLoad).isFalse()
        assertThat(viewModel.state.isRefreshing).isTrue()
        gate.complete(page(memory("likes_tea")))
        advanceUntilIdle()
        assertThat(viewModel.state.isRefreshing).isFalse()
    }

    @Test
    fun `a failed refresh keeps the list and reports an event with Retry`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Timeout)
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.state.memories).hasSize(1)
        assertThat(viewModel.state.loadError).isNull()
        assertThat(viewModel.state.isRefreshing).isFalse()
        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.operation).isEqualTo(MemoryOperation.Refresh)
        assertThat(event.failure).isEqualTo(MemoryFailure.Network)
        assertThat(event.canRetry).isTrue()
    }

    /** A refresh answered before a delete must not land after the delete's reload and revive the row. */
    @Test
    fun `a reload after a delete wins over a refresh still in flight`() = runTest {
        val tea = memory("likes_tea")
        val coffee = memory("likes_coffee")
        coEvery { memoryRepository.getMemories() } returns page(tea, coffee)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val staleRefresh = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { staleRefresh.await() }
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.state.isRefreshing).isTrue()

        coEvery { memoryRepository.getMemories() } returns page(coffee)
        viewModel.deleteMemory(tea)
        advanceUntilIdle()
        staleRefresh.complete(page(tea, coffee))
        advanceUntilIdle()

        assertThat(viewModel.state.memories.map { it.key }).containsExactly("likes_coffee")
        assertThat(viewModel.state.isRefreshing).isFalse()
    }

    // --------------------------------------------------------------------------- refreshOnReturn

    @Test
    fun `refreshOnReturn skips the first resume`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.getMemories() }

        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coVerify(exactly = 2) { memoryRepository.getMemories() }
        assertThat(viewModel.state.isRefreshing).isFalse()
    }

    @Test
    fun `refreshOnReturn skips while a load is running`() = runTest {
        val gate = CompletableDeferred<Result<MemoriesResponse>>()
        coEvery { memoryRepository.getMemories() } coAnswers { gate.await() }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryRepository.getMemories() }
        gate.complete(page())
        advanceUntilIdle()
    }

    @Test
    fun `a quiet reload failure after a return is silent`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.refreshOnReturn()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(events).isEmpty()
        assertThat(viewModel.state.memories).hasSize(1)
    }

    // ------------------------------------------------------------------------------------ search

    @Test
    fun `search stays hidden and inert below five memories`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            memory("a"), memory("b"), memory("c"), memory("d"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQuery("zzz")
        advanceUntilIdle()

        assertThat(viewModel.state.showSearch).isFalse()
        assertThat(viewModel.state.visibleMemories).hasSize(4)
        assertThat(viewModel.state.isSearchEmpty).isFalse()
    }

    @Test
    fun `search matches key or value, ignoring case`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            memory("likes_tea", value = "Earl Grey"),
            memory("home_city", value = "Lives in Lisbon"),
            memory("pet", value = "a cat named TEApot"),
            memory("job", value = "engineer"),
            memory("sport", value = "climbing"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.showSearch).isTrue()

        viewModel.updateQuery("  tea ")
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories.map { it.key }).containsExactly("likes_tea", "pet").inOrder()

        viewModel.updateQuery("LISBON")
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories.map { it.key }).containsExactly("home_city")

        viewModel.updateQuery("nothing matches")
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories).isEmpty()
        assertThat(viewModel.state.isSearchEmpty).isTrue()
    }

    // --------------------------------------------------------------------------------- partition

    @Test
    fun `no chips when no memory belongs to an agent`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("a"), memory("b"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.partitionOptions).isNull()
    }

    @Test
    fun `chips are All, Personal and one per agent, with the name when the server resolved it`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            memory("a"),
            memory("b", agentId = "agent_1", agentName = "Researcher"),
            memory("c", agentId = "agent_2"),
            memory("d", agentId = "agent_1", agentName = "Researcher"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.partitionOptions).containsExactly(
            MemoryPartitionOption(MemoryPartition.All),
            MemoryPartitionOption(MemoryPartition.Personal),
            MemoryPartitionOption(MemoryPartition.Agent("agent_1"), agentName = "Researcher"),
            MemoryPartitionOption(MemoryPartition.Agent("agent_2"), agentName = null),
        ).inOrder()

        viewModel.selectPartition(MemoryPartition.Personal)
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories.map { it.key }).containsExactly("a")

        viewModel.selectPartition(MemoryPartition.Agent("agent_1"))
        advanceUntilIdle()
        assertThat(viewModel.state.visibleMemories.map { it.key }).containsExactly("b", "d").inOrder()
    }

    /** Two agents the server can't name would both read "Agent memory"; two may share a name. */
    @Test
    fun `agents whose labels would repeat are numbered, on the chips and the cards`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            // Last updated first: agent_gone_2's memory is newer, but the numbers follow the id, so
            // an edit can't swap them between reloads.
            memory("c", agentId = "agent_gone_2"),
            memory("b", agentId = "agent_helper_1", agentName = "Helper"),
            memory("a", agentId = "agent_gone_1"),
            memory("d", agentId = "agent_helper_2", agentName = "helper "),
            memory("e", agentId = "agent_research", agentName = "Researcher"),
            memory("f", agentId = "agent_gone_1"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.partitionOptions).containsExactly(
            MemoryPartitionOption(MemoryPartition.All),
            MemoryPartitionOption(MemoryPartition.Personal),
            MemoryPartitionOption(MemoryPartition.Agent("agent_gone_2"), agentName = null, ordinal = 2),
            MemoryPartitionOption(MemoryPartition.Agent("agent_helper_1"), agentName = "Helper", ordinal = 1),
            MemoryPartitionOption(MemoryPartition.Agent("agent_gone_1"), agentName = null, ordinal = 1),
            MemoryPartitionOption(MemoryPartition.Agent("agent_helper_2"), agentName = "helper ", ordinal = 2),
            MemoryPartitionOption(MemoryPartition.Agent("agent_research"), agentName = "Researcher"),
        ).inOrder()
        assertThat(viewModel.state.agentOrdinals).containsExactly(
            "agent_gone_1", 1,
            "agent_helper_1", 1,
            "agent_gone_2", 2,
            "agent_helper_2", 2,
        )
    }

    @Test
    fun `one unnamed agent is not numbered`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            memory("a", agentId = "agent_gone"),
            memory("b", agentId = "agent_1", agentName = "Researcher"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.partitionOptions?.mapNotNull { it.ordinal }).isEmpty()
        assertThat(viewModel.state.agentOrdinals).isEmpty()
    }

    @Test
    fun `an agent chip whose memories are gone falls back to All`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(
            memory("a"),
            memory("b", agentId = "agent_1"),
            memory("c", agentId = "agent_2"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectPartition(MemoryPartition.Agent("agent_1"))
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns page(memory("a"), memory("c", agentId = "agent_2"))
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.state.partition).isEqualTo(MemoryPartition.All)
        assertThat(viewModel.state.visibleMemories).hasSize(2)
    }

    @Test
    fun `any chip falls back to All once no agent memory is left`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("a"), memory("b", agentId = "agent_1"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectPartition(MemoryPartition.Personal)
        advanceUntilIdle()

        coEvery { memoryRepository.getMemories() } returns page(memory("a"))
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.state.partitionOptions).isNull()
        assertThat(viewModel.state.partition).isEqualTo(MemoryPartition.All)
    }

    // ---------------------------------------------------------------------------------- gating

    @Test
    fun `gates are permissive while the role is unknown or lacks the keys`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        with(viewModel.state) {
            assertThat(listOf(canRead, canCreate, canUpdate, canOptOut)).containsExactly(true, true, true, true)
        }

        role.value = memoriesRole("USE" to true)
        advanceUntilIdle()
        with(viewModel.state) {
            assertThat(listOf(canRead, canCreate, canUpdate, canOptOut)).containsExactly(true, true, true, true)
        }
    }

    @Test
    fun `each gate follows its own bit and USE`() = runTest {
        role.value = memoriesRole("USE" to true, "READ" to true, "CREATE" to false, "UPDATE" to true, "OPT_OUT" to false)
        val viewModel = createViewModel()
        advanceUntilIdle()
        with(viewModel.state) {
            assertThat(canRead).isTrue()
            assertThat(canCreate).isFalse()
            assertThat(canUpdate).isTrue()
            assertThat(canOptOut).isFalse()
        }

        role.value = memoriesRole("USE" to false)
        advanceUntilIdle()
        with(viewModel.state) {
            assertThat(listOf(canRead, canCreate, canUpdate, canOptOut)).containsExactly(false, false, false, false)
        }
    }

    /** The Data row and chat's Manage are permissive while the role loads, so the page can open first. */
    @Test
    fun `a role that arrives without READ or OPT_OUT turns the loaded page into the no-access state`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.noAccess).isFalse()

        role.value = memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to false)
        advanceUntilIdle()

        assertThat(viewModel.state.canRead).isFalse()
        assertThat(viewModel.state.noAccess).isTrue()
        assertThat(viewModel.state.switchOnly).isFalse()
    }

    @Test
    fun `a role that arrives without READ but with OPT_OUT leaves only the switch`() = runTest {
        coEvery { memoryRepository.getMemories() } returns page(memory("likes_tea"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        role.value = memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to true)
        advanceUntilIdle()

        with(viewModel.state) {
            assertThat(listDenied).isTrue()
            assertThat(switchOnly).isTrue()
            assertThat(noAccess).isFalse()
            assertThat(canOptOut).isTrue()
        }
    }

    /** The opt-out route checks USE and OPT_OUT only, so a role without READ may still opt out. */
    @Test
    fun `without READ the page is the switch alone, never loads the list, and toggles`() = runTest {
        role.value = memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        with(viewModel.state) {
            assertThat(switchOnly).isTrue()
            assertThat(noAccess).isFalse()
            assertThat(isFirstLoad).isFalse()
            assertThat(loadError).isNull()
            assertThat(memoriesEnabled).isTrue()
        }

        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        coVerify(exactly = 1) { memoryRepository.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false)) }

        // Pull to refresh, a return to the page and the banner's retry read the opt-out, not the list.
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.state.isRefreshing).isFalse()
        viewModel.refreshOnReturn()
        viewModel.refreshOnReturn()
        viewModel.retryLoad()
        advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.getMemories() }
        coVerify(atLeast = 2) { memoryRepository.refreshMemoriesPreference() }
    }

    @Test
    fun `a 403 on the first load keeps the switch when the role grants OPT_OUT`() = runTest {
        coEvery { memoryRepository.getMemories() } returns serverError(403, "Forbidden", body = """{"message":"Forbidden"}""")
        role.value = memoriesRole("USE" to true, "OPT_OUT" to true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.loadError).isEqualTo(MemoryFailure.Forbidden)
        assertThat(viewModel.state.switchOnly).isTrue()
        assertThat(viewModel.state.noAccess).isFalse()
    }

    /** That 403 could be a missing USE, which would refuse the opt-out as well. */
    @Test
    fun `a 403 on the first load is no access when OPT_OUT is only assumed`() = runTest {
        coEvery { memoryRepository.getMemories() } returns serverError(403, "Forbidden", body = """{"message":"Forbidden"}""")
        role.value = memoriesRole("USE" to true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.noAccess).isTrue()
        assertThat(viewModel.state.switchOnly).isFalse()
    }

    @Test
    fun `a role without USE has no access even with READ`() = runTest {
        role.value = memoriesRole("USE" to false, "READ" to true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.noAccess).isTrue()
    }

    @Test
    fun `a 403 on the first load is the no-access state`() = runTest {
        coEvery { memoryRepository.getMemories() } returns serverError(403, "Forbidden", body = """{"message":"Forbidden"}""")
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.loadError).isEqualTo(MemoryFailure.Forbidden)
        assertThat(viewModel.state.noAccess).isTrue()
    }

    @Test
    fun `any other first-load failure keeps the page and its banner`() = runTest {
        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.noAccess).isFalse()
    }

    @Test
    fun `without CREATE the sheet does not open for a new memory`() = runTest {
        role.value = memoriesRole("CREATE" to false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openCreate()
        advanceUntilIdle()

        assertThat(viewModel.state.editor).isNull()
    }

    @Test
    fun `without UPDATE a memory opens read-only and cannot be deleted`() = runTest {
        val tea = memory("likes_tea")
        coEvery { memoryRepository.getMemories() } returns page(tea)
        role.value = memoriesRole("UPDATE" to false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(tea)
        viewModel.updateEditorValue("changed")
        viewModel.saveEditor()
        viewModel.deleteMemory(tea)
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.mode).isEqualTo(MemoryEditorMode.View)
        assertThat(viewModel.state.editor?.value).isEqualTo(tea.value)
        assertThat(viewModel.state.editor?.canSave).isFalse()
        coVerify(exactly = 0) { memoryRepository.updateMemory(any(), any(), any()) }
        coVerify(exactly = 0) { memoryRepository.deleteMemory(any()) }
    }

    @Test
    fun `a content-filtered memory opens read-only even with UPDATE`() = runTest {
        val hidden = memory("", value = "", blocked = true)
        coEvery { memoryRepository.getMemories() } returns page(hidden)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openMemory(hidden)
        advanceUntilIdle()

        assertThat(viewModel.state.editor?.mode).isEqualTo(MemoryEditorMode.View)
        assertThat(viewModel.state.editor?.isDirty).isFalse()
    }

    // ---------------------------------------------------------------------------------- opt-out

    @Test
    fun `the switch has no value until the preference is known`() = runTest {
        storedPreference = null
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.memoriesEnabled).isNull()
        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        coVerify(exactly = 0) { memoryRepository.updatePreferences(any()) }
    }

    /** A failed read must not look like one still loading: the card offers Retry instead. */
    @Test
    fun `a failed preference read is kept until a retry reads it`() = runTest {
        storedPreference = null
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isNull()
        assertThat(viewModel.state.preferenceLoadFailed).isTrue()

        val gate = CompletableDeferred<Unit>()
        coEvery { memoryRepository.refreshMemoriesPreference() } coAnswers {
            gate.await()
            memoriesEnabled.value = false
            Result.Success(false)
        }
        viewModel.retryPreference()
        advanceUntilIdle()
        // Reading again: back to "loading", not still "failed".
        assertThat(viewModel.state.preferenceLoadFailed).isFalse()

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        assertThat(viewModel.state.preferenceLoadFailed).isFalse()
        coVerify(exactly = 2) { memoryRepository.refreshMemoriesPreference() }
    }

    @Test
    fun `a failed preference read is not shown once the value is known`() = runTest {
        storedPreference = null
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.preferenceLoadFailed).isTrue()

        // Another screen read it meanwhile.
        memoriesEnabled.value = true
        advanceUntilIdle()
        assertThat(viewModel.state.preferenceLoadFailed).isFalse()
    }

    @Test
    fun `the switch reads the shared flow, seeded by a preference read on open`() = runTest {
        storedPreference = false
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        coVerify(exactly = 1) { memoryRepository.refreshMemoriesPreference() }

        // Another screen (or account) moves the shared value.
        memoriesEnabled.value = true
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isTrue()
    }

    @Test
    fun `a toggle in flight blocks a second one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { memoryRepository.updatePreferences(any()) } coAnswers {
            gate.await()
            memoriesEnabled.value = false
            Result.Success(MemoryPreferences(enabled = false))
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        assertThat(viewModel.state.isTogglingPreference).isTrue()
        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.updatePreferences(any()) }
        assertThat(viewModel.state.isTogglingPreference).isFalse()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
    }

    @Test
    fun `without OPT_OUT the switch sends nothing`() = runTest {
        role.value = memoriesRole("OPT_OUT" to false)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 0) { memoryRepository.updatePreferences(any()) }
    }

    @Test
    fun `a failed toggle is an event whose Retry sends the same value`() = runTest {
        coEvery { memoryRepository.updatePreferences(any()) } returns Result.Error(kind = FailureKind.Network)
        val viewModel = createViewModel()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.updatePreferences(any()) }

        val event = events.single() as MemoriesEvent.Failed
        assertThat(event.operation).isEqualTo(MemoryOperation.SetEnabled(false))
        assertThat(event.canRetry).isTrue()
        assertThat(viewModel.state.memoriesEnabled).isTrue()

        viewModel.retry(checkNotNull(event.retryOperation))
        advanceUntilIdle()
        coVerify(exactly = 2) { memoryRepository.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false)) }
    }

    /** The user switched memories off on the web; pull to refresh (or coming back) must show it. */
    @Test
    fun `refresh, a return to the page and retry each read the opt-out again`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isTrue()

        storedPreference = false
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        coVerify(exactly = 2) { memoryRepository.refreshMemoriesPreference() }

        // The first resume follows the open; only the second is a return.
        storedPreference = true
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coVerify(exactly = 2) { memoryRepository.refreshMemoriesPreference() }
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isTrue()
        coVerify(exactly = 3) { memoryRepository.refreshMemoriesPreference() }

        coEvery { memoryRepository.getMemories() } returns Result.Error(kind = FailureKind.Network)
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coEvery { memoryRepository.getMemories() } returns Result.Success(MemoriesResponse())
        storedPreference = false
        viewModel.retryLoad()
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        coVerify(exactly = 5) { memoryRepository.refreshMemoriesPreference() }
    }

    /**
     * A read started while the PATCH runs could answer with the value from before it. The repository
     * also refuses to publish such a read, but the ViewModel must not start one.
     */
    @Test
    fun `a refresh during a toggle does not read the opt-out`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { memoryRepository.updatePreferences(any()) } coAnswers {
            gate.await()
            memoriesEnabled.value = false
            Result.Success(MemoryPreferences(enabled = false))
        }
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMemoriesEnabled(false)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        coVerify(exactly = 1) { memoryRepository.refreshMemoriesPreference() }

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.state.memoriesEnabled).isFalse()
        coVerify(exactly = 1) { memoryRepository.refreshMemoriesPreference() }
    }
}
