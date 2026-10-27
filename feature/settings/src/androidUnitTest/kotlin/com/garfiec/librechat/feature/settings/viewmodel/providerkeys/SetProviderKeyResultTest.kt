package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A saved or revoked key's result: the sheet stays busy from the server's answer until it has acted
 * on the result, so nothing can be tapped while the sheet is about to close. The result is state, so
 * a sheet recreated before it acted on it still closes; a result of an earlier open frees the sheet
 * without closing it; and a result the sheet never acted on doesn't hold the next open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeyResultTest {

    private val testDispatcher = StandardTestDispatcher()

    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)

    private val neverExpiring = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
        coEvery { keyRepository.updateKey(any()) } returns Result.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun configFlow(name: String) =
        MutableStateFlow(mapOf(name to EndpointConfig(name = name, userProvide = true)))

    private fun TestScope.subscribeEffects(vm: SetProviderKeyViewModel): MutableList<SetProviderKeyEffect> {
        val collected = mutableListOf<SetProviderKeyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { collected += it }
        }
        return collected
    }

    /**
     * Acts on results as the sheet showing [openRequest] does: later than the state change
     * (Compose's UI dispatcher, here the test's StandardTestDispatcher), through the VM's own
     * decision. Records whether the sheet was busy when the result reached it, and the decision.
     */
    private fun TestScope.actOnResultsLikeTheSheet(
        vm: SetProviderKeyViewModel,
        openRequest: Long,
    ): MutableList<Pair<Boolean, Boolean>> {
        val busyAndClosed = mutableListOf<Pair<Boolean, Boolean>>()
        backgroundScope.launch {
            vm.uiState.mapNotNull { it.pendingResult }.collect { result ->
                val busy = vm.uiState.value.isBusy
                busyAndClosed += busy to vm.onMutationResult(result, openRequest)
            }
        }
        return busyAndClosed
    }

    @Test
    fun the_sheet_stays_busy_after_a_save_until_its_result_is_handled() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        val busyAndClosed = actOnResultsLikeTheSheet(vm, openRequest = 1)

        vm.updateField(ProviderKeyField.API_KEY, "sk-new")
        vm.save()
        advanceUntilIdle()
        runCurrent()

        // While the result was on its way the sheet could not be closed, saved or revoked; then it closed.
        assertThat(busyAndClosed).containsExactly(true to true)
        assertThat(vm.uiState.value.isBusy).isFalse()
    }

    @Test
    fun the_sheet_stays_busy_after_a_revoke_until_its_result_is_handled() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)
        vm.onOpened(1, neverExpiring)
        advanceUntilIdle()
        val busyAndClosed = actOnResultsLikeTheSheet(vm, openRequest = 1)

        vm.revoke()
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left; let the collector run.
        runCurrent()

        assertThat(busyAndClosed).containsExactly(true to true)
        assertThat(vm.uiState.value.isBusy).isFalse()
    }

    @Test
    fun a_result_waiting_to_be_handled_refuses_revoke_save_and_discard() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        vm.updateField(ProviderKeyField.API_KEY, "sk-new")
        vm.save()
        advanceUntilIdle()
        // The sheet hasn't acted on the result yet: the key is saved, the sheet is still up.
        assertThat(vm.uiState.value.resultPending).isTrue()
        assertThat(vm.uiState.value.canRevoke).isTrue()

        vm.revoke()
        vm.updateField(ProviderKeyField.API_KEY, "sk-again")
        vm.save()
        vm.discardDraft()
        advanceUntilIdle()

        assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEqualTo("sk-again")
        coVerify(exactly = 0) { keyRepository.deleteKey(any()) }
        coVerify(exactly = 1) { keyRepository.updateKey(any()) }
        // Only the pending result itself releases it.
        val result = checkNotNull(vm.uiState.value.pendingResult)
        assertThat(vm.onMutationResult(result.copy(), currentOpenRequest = 1)).isFalse()
        assertThat(vm.uiState.value.isBusy).isTrue()
        assertThat(vm.onMutationResult(result, currentOpenRequest = 1)).isTrue()
        assertThat(vm.uiState.value.isBusy).isFalse()
        // Acting on it twice (a second sheet composition) closes nothing more.
        assertThat(vm.onMutationResult(result, currentOpenRequest = 1)).isFalse()
    }

    @Test
    fun a_new_open_after_a_result_the_sheet_never_handled_starts_blank_and_idle() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        vm.updateField(ProviderKeyField.API_KEY, "sk-new")
        vm.save()
        advanceUntilIdle()
        // The sheet left the composition before it acted on the result.
        assertThat(vm.uiState.value.isBusy).isTrue()

        vm.onOpened(2, neverExpiring)
        val collected = subscribeEffects(vm)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isBusy).isFalse()
        assertThat(vm.uiState.value.canSubmit).isTrue()
        // The stranded result was dropped, so it can't close the new sheet.
        assertThat(vm.uiState.value.pendingResult).isNull()
        assertThat(collected).isEmpty()
    }

    @Test
    fun a_sheet_recreated_before_it_acted_on_a_result_still_closes_and_is_not_left_busy() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
            val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
            vm.onOpened(1, KeyState.Unset)
            advanceUntilIdle()
            vm.updateField(ProviderKeyField.API_KEY, "sk-new")
            vm.save()
            advanceUntilIdle()
            // The activity was recreated (rotation, a fold) before the sheet acted on the result.
            val result = checkNotNull(vm.uiState.value.pendingResult)

            // The recreated sheet composes again for the same open: a restoration, not a new open.
            vm.onOpened(1, KeyState.Unset)
            advanceUntilIdle()
            assertThat(vm.uiState.value.pendingResult).isSameInstanceAs(result)
            assertThat(vm.uiState.value.isBusy).isTrue()

            // It sees the result as state and closes on it.
            val busyAndClosed = actOnResultsLikeTheSheet(vm, openRequest = 1)
            runCurrent()

            assertThat(busyAndClosed).containsExactly(true to true)
            assertThat(vm.uiState.value.isBusy).isFalse()
        }

    @Test
    fun a_result_of_an_earlier_open_frees_the_sheet_reopened_meanwhile_without_closing_it() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
            val saveGate = CompletableDeferred<Unit>()
            coEvery { keyRepository.updateKey(any()) } coAnswers {
                saveGate.await()
                Result.Success(Unit)
            }
            val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
            vm.onOpened(1, KeyState.Unset)
            advanceUntilIdle()
            vm.updateField(ProviderKeyField.API_KEY, "sk-new")
            vm.save()
            advanceUntilIdle()
            assertThat(vm.uiState.value.isSaving).isTrue()

            // A deep link or a row tap opens the sheet again while the save is out: the form stays.
            vm.onOpened(2, KeyState.Unset)
            advanceUntilIdle()
            assertThat(vm.uiState.value.isSaving).isTrue()
            assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey)
                .isEqualTo("sk-new")

            val busyAndClosed = actOnResultsLikeTheSheet(vm, openRequest = 2)
            saveGate.complete(Unit)
            advanceUntilIdle()
            runCurrent()

            // The result belongs to open 1: the sheet showing open 2 isn't closed, but is free again.
            assertThat(busyAndClosed).containsExactly(true to false)
            assertThat(vm.uiState.value.pendingResult).isNull()
            assertThat(vm.uiState.value.isBusy).isFalse()
            assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEmpty()
        }
}
