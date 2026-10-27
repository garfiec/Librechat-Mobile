package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_success
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_success
import com.garfiec.librechat.feature.settings.state.providerkeys.liveKeyCount
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderKeysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val permissions = MutableStateFlow<UserRolePermissions?>(null)
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns permissions
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
        every { configRepository.startupConfig } returns startupConfig
    }

    private fun newVm(handle: SavedStateHandle = SavedStateHandle()) =
        ProviderKeysViewModel(keyRepository, configRepository, roleRepository, handle)

    /** What the system hands a recreated process: the same keys and values, in a new handle. */
    private fun SavedStateHandle.restored(): SavedStateHandle =
        SavedStateHandle(keys().associateWith<String, Any?> { this[it] })

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setEndpoints(map: Map<String, EndpointConfig>) {
        val flow = MutableStateFlow(map)
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { configRepository.fetchEndpoints() } returns Result.Success(map)
        coEvery { configRepository.fetchModels() } returns Result.Success(emptyMap())
    }

    @Test
    fun filters_endpoints_without_a_user_provided_value() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
                "no-key" to EndpointConfig(name = "no-key", userProvide = false, userProvideURL = false),
                "custom-url" to EndpointConfig(name = "custom-url", userProvide = false, userProvideURL = true),
                "no-fields" to EndpointConfig(name = "no-fields"),
                "bedrock" to EndpointConfig(name = "bedrock", userProvideBearerToken = true),
            ),
        )
        val vm = newVm()
        advanceUntilIdle()
        val names = vm.uiState.value.entries.map { it.endpointName }.toSet()
        // Base-URL-only endpoints stay (deliberately; web drops them), and a Bedrock credential
        // flag counts on its own.
        assertThat(names).containsExactly("openAI", "google", "custom-url", "bedrock")
    }

    @Test
    fun entries_follow_model_specs_and_display_order() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "zeta" to EndpointConfig(userProvide = true, order = 1),
                "alpha" to EndpointConfig(userProvide = true, order = 2),
                "beta" to EndpointConfig(userProvide = true, order = 1, modelDisplayLabel = "Able"),
                "hidden" to EndpointConfig(userProvide = true, order = 0),
            ),
        )
        startupConfig.value = StartupConfig(
            modelSpecs = ModelSpecs(
                list = listOf(ModelSpec(name = "z", preset = Preset(endpoint = "zeta"))),
                addedEndpoints = listOf("alpha", "beta"),
            ),
        )
        val vm = newVm()
        advanceUntilIdle()

        // `hidden` isn't reachable through the specs; the rest sort by order, then label.
        assertThat(vm.uiState.value.entries.map { it.endpointName })
            .containsExactly("beta", "zeta", "alpha").inOrder()
    }

    @Test
    fun agent_providers_need_agents_use() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
                "agents" to EndpointConfig(allowedProviders = listOf("anthropic")),
            ),
        )
        startupConfig.value = StartupConfig(
            modelSpecs = ModelSpecs(
                list = listOf(ModelSpec(name = "o", preset = Preset(endpoint = "openAI"))),
                addedEndpoints = listOf("agents"),
            ),
        )
        // Unknown role: permissive, like the app's other USE gates.
        val vm = newVm()
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.map { it.endpointName }).containsExactly("anthropic", "openAI")

        permissions.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("AGENTS" to mapOf("USE" to false)),
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.map { it.endpointName }).containsExactly("openAI")
    }

    @Test
    fun summary_counts_live_keys_over_listed_endpoints() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
                "google" to EndpointConfig(userProvide = true),
            ),
        )
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        coEvery { keyRepository.fetchKeyState("anthropic") } returns Result.Success(EXPIRED_KEY)
        val vm = newVm()
        advanceUntilIdle()

        assertThat(vm.uiState.value.entries.liveKeyCount(Clock.System.now())).isEqualTo(1)
        assertThat(vm.uiState.value.entries).hasSize(3)
        assertThat(vm.uiState.value.canRevokeAll).isTrue()
    }

    @Test
    fun failed_key_fetch_with_no_earlier_answer_marks_the_row_and_retry_rechecks_it() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")
        val vm = newVm()
        advanceUntilIdle()

        val failed = vm.uiState.value.entries.single()
        assertThat(failed.fetchFailed).isTrue()
        assertThat(failed.isChecking).isFalse()
        // Not guessed as "Not set".
        assertThat(failed.keyState).isEqualTo(KeyState.Loading)

        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        vm.retry("openAI")
        // The row keeps its Retry (busy) until the answer lands, so the button never leaves
        // from under a screen reader's focus, and the summary doesn't flip to counting.
        val retrying = vm.uiState.value.entries.single()
        assertThat(retrying.retrying).isTrue()
        assertThat(retrying.fetchFailed).isTrue()
        assertThat(retrying.isChecking).isFalse()
        advanceUntilIdle()

        val recovered = vm.uiState.value.entries.single()
        assertThat(recovered.fetchFailed).isFalse()
        assertThat(recovered.retrying).isFalse()
        assertThat(recovered.keyState).isInstanceOf(KeyState.Set::class.java)
    }

    @Test
    fun a_retry_that_fails_again_keeps_the_row_unknown_with_its_retry() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")
        val vm = newVm()
        advanceUntilIdle()

        vm.retry("openAI")
        vm.retry("openAI")
        advanceUntilIdle()

        // One re-read for the two taps; the second came while the first was out.
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        val row = vm.uiState.value.entries.single()
        assertThat(row.fetchFailed).isTrue()
        assertThat(row.retrying).isFalse()
        assertThat(row.keyState).isEqualTo(KeyState.Loading)
    }

    @Test
    fun failed_key_fetch_keeps_the_last_known_state() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        val vm = newVm()
        advanceUntilIdle()

        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")
        vm.refreshOnReturn() // first resume: the initial load's
        vm.refreshOnReturn()
        advanceUntilIdle()

        val entry = vm.uiState.value.entries.single()
        assertThat(entry.keyState).isInstanceOf(KeyState.Set::class.java)
        assertThat(entry.fetchFailed).isFalse()
    }

    @Test
    fun config_load_failure_is_an_error_state_with_retry() = runTest(testDispatcher) {
        val configs = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { configRepository.endpointConfigs } returns configs
        coEvery { configRepository.fetchEndpoints() } returns Result.Error(message = "offline")
        val vm = newVm()
        advanceUntilIdle()

        assertThat(vm.uiState.value.loadError).isTrue()
        assertThat(vm.uiState.value.isLoading).isFalse()

        val loaded = mapOf("openAI" to EndpointConfig(userProvide = true))
        coEvery { configRepository.fetchEndpoints() } coAnswers {
            configs.value = loaded
            Result.Success(loaded)
        }
        vm.retry()
        advanceUntilIdle()

        assertThat(vm.uiState.value.loadError).isFalse()
        assertThat(vm.uiState.value.entries.map { it.endpointName }).containsExactly("openAI")
    }

    @Test
    fun refreshOnReturn_is_quiet_and_leaves_the_sheet_alone() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        val vm = newVm()
        advanceUntilIdle()
        vm.openDialog("openAI")
        val openRequest = vm.uiState.value.dialogOpenRequest

        vm.refreshOnReturn() // first resume belongs to the initial load
        advanceUntilIdle()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

        val loadingSeen = mutableListOf<Boolean>()
        val watcher = launch { vm.uiState.collect { loadingSeen += it.isLoading } }
        vm.refreshOnReturn()
        advanceUntilIdle()
        watcher.cancel()

        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        assertThat(loadingSeen).doesNotContain(true)
        assertThat(vm.uiState.value.pendingDialogEndpoint).isEqualTo("openAI")
        assertThat(vm.uiState.value.dialogOpenRequest).isEqualTo(openRequest)
    }

    @Test
    fun open_sheet_survives_process_death() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        val handle = SavedStateHandle()
        val vm = newVm(handle)
        advanceUntilIdle()
        vm.openDialog("openAI")
        val openRequest = vm.uiState.value.dialogOpenRequest

        val restoredHandle = handle.restored()
        val recreated = newVm(restoredHandle)
        assertThat(recreated.uiState.value.pendingDialogEndpoint).isEqualTo("openAI")
        // The same open request, so the sheet keeps its restored draft instead of resetting.
        assertThat(recreated.uiState.value.dialogOpenRequest).isEqualTo(openRequest)

        // A dismissed sheet stays dismissed through the next death.
        recreated.dismissDialog()
        assertThat(newVm(restoredHandle.restored()).uiState.value.pendingDialogEndpoint).isNull()
    }

    @Test
    fun every_explicit_open_is_a_new_open_request() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        val vm = newVm()
        advanceUntilIdle()
        vm.openDialog("openAI")
        val first = vm.uiState.value.dialogOpenRequest
        vm.dismissDialog()
        vm.openDialog("openAI")
        assertThat(vm.uiState.value.dialogOpenRequest).isGreaterThan(first)
    }

    @Test
    fun revokeAll_is_single_flight_and_keeps_the_confirm_up_until_answered() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        val gate = CompletableDeferred<Unit>()
        coEvery { keyRepository.deleteAllKeys() } coAnswers {
            gate.await()
            Result.Success(Unit)
        }
        val vm = newVm()
        advanceUntilIdle()
        vm.showRevokeAllConfirm()

        vm.revokeAll()
        vm.revokeAll()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isRevokingAll).isTrue()
        assertThat(vm.uiState.value.showRevokeAllConfirm).isTrue()
        vm.dismissRevokeAllConfirm()
        assertThat(vm.uiState.value.showRevokeAllConfirm).isTrue()

        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { keyRepository.deleteAllKeys() }
        assertThat(vm.uiState.value.isRevokingAll).isFalse()
        assertThat(vm.uiState.value.showRevokeAllConfirm).isFalse()
    }

    @Test
    fun revokeAll_failure_shows_the_translated_message_not_the_servers() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(userProvide = true)))
        coEvery { keyRepository.deleteAllKeys() } returns Result.Error(message = "E11000 duplicate key")
        val vm = newVm()
        advanceUntilIdle()
        vm.showRevokeAllConfirm()
        vm.revokeAll()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(Res.string.provider_keys_revoke_all_failed)
        assertThat(vm.uiState.value.showRevokeAllConfirm).isFalse()
    }

    @Test
    fun canRevokeAll_waits_for_every_row() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
            ),
        )
        val gate = CompletableDeferred<Unit>()
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
        coEvery { keyRepository.fetchKeyState("anthropic") } coAnswers {
            gate.await()
            Result.Success(KeyState.Unset)
        }
        val vm = newVm()
        advanceUntilIdle()
        assertThat(vm.uiState.value.canRevokeAll).isFalse()

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.canRevokeAll).isTrue()
    }

    @Test
    fun revokeAll_invokes_fetchEndpoints_and_fetchModels() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        val vm = newVm()
        advanceUntilIdle()
        vm.revokeAll()
        advanceUntilIdle()

        coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
        coVerify(atLeast = 1) { configRepository.fetchModels() }
        coVerify(exactly = 1) { keyRepository.deleteAllKeys() }
    }

    @Test
    fun onChildKeyChanged_invalidates_models_cache() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        val vm = newVm()
        advanceUntilIdle()
        vm.onChildKeyChanged("openAI")
        advanceUntilIdle()

        coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
        coVerify(atLeast = 1) { configRepository.fetchModels() }
    }

    @Test
    fun onChildKeyChanged_reloads_endpoints_and_models_even_after_the_screen_is_gone() =
        runTest(testDispatcher) {
            // A save reached from chat pops this screen at once (its ViewModel is cleared), and the
            // chat's model list depends on what these two calls load.
            setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
            // Main.immediate in production: the launch runs up to its first suspension at once.
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val store = ViewModelStore()
            val vm = ViewModelProvider.create(store, viewModelFactory { initializer { newVm() } })[ProviderKeysViewModel::class]
            advanceUntilIdle()
            val endpointsGate = CompletableDeferred<Unit>()
            coEvery { configRepository.fetchEndpoints() } coAnswers {
                endpointsGate.await()
                Result.Success(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
            }

            vm.onChildKeyChanged("openAI")
            store.clear()
            endpointsGate.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { configRepository.fetchEndpoints() }
            coVerify(exactly = 1) { configRepository.fetchModels() }
        }

    @Test
    fun onChildKeyChanged_cancels_stale_refresh_when_called_in_quick_succession() =
        runTest(testDispatcher) {
            // A stale fan-out (slow first call) must not overwrite fresh state from a later
            // call. Mockk's coEvery returns immediately, so we don't need to interleave
            // delays; back-to-back invocations should still each reach fetchEndpoints, but
            // the cancel-and-restart pattern via `refreshJob` ensures only the latest job's
            // result is observed.
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            val vm = newVm()
            advanceUntilIdle()

            vm.onChildKeyChanged("openAI")
            vm.onChildKeyChanged("openAI")
            advanceUntilIdle()

            // Cache invalidation still happens for the latest job.
            coVerify(atLeast = 1) { configRepository.fetchEndpoints() }
            coVerify(atLeast = 1) { configRepository.fetchModels() }
        }

    @Test
    fun onChildKeyChanged_refetches_only_the_named_endpoints_keystate() =
        runTest(testDispatcher) {
            // `refresh(forceFor=...)` contract: only the named endpoints get their key state
            // re-fetched; other rows reuse the cached KeyState. Two endpoints; fetch is
            // verified to be called once per endpoint at init, then once more for the
            // explicitly-refreshed one after onChildKeyChanged.
            setEndpoints(
                mapOf(
                    "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                    "google" to EndpointConfig(name = "google", userProvide = true),
                ),
            )
            val vm = newVm()
            advanceUntilIdle()

            // Initial load: each endpoint's key state fetched once.
            coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
            coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }

            vm.onChildKeyChanged("openAI")
            advanceUntilIdle()

            // Only the named endpoint is re-fetched; google reuses the cached value.
            coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
            coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }
        }

    @Test
    fun revokeAll_success_force_refetches_all_keystates() = runTest(testDispatcher) {
        // `refresh(forceAll=true)` contract: revokeAll re-fetches every row's key state
        // from the server even when the entry list is unchanged.
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
            ),
        )
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

        val vm = newVm()
        advanceUntilIdle()
        // Initial load: 1 fetch per endpoint.
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }

        vm.revokeAll()
        advanceUntilIdle()

        // Both endpoints re-fetched (forceAll=true).
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 2) { keyRepository.fetchKeyState("google") }
    }

    // The next two tests exercise the public-method composition contract: concurrent /
    // sequential calls to public mutators compose their effects without losing writes.
    // CAS retries are inherently non-deterministic, so the race is not directly exercised.

    @Test
    fun concurrent_revokeAll_then_showRevokeAllConfirm_compose_without_overwrite() =
        runTest(testDispatcher) {
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)

            val vm = newVm()
            advanceUntilIdle()
            vm.emitTransientMessage(Res.string.provider_keys_save_success)

            // Launch revokeAll + showRevokeAllConfirm concurrently. Both touch _uiState.
            launch { vm.revokeAll() }
            launch { vm.showRevokeAllConfirm() }
            advanceUntilIdle()

            // Both effects must have landed: revokeAll finished (isRevokingAll = false), and
            // the transient message survives both updates.
            assertThat(vm.uiState.value.isRevokingAll).isFalse()
            assertThat(vm.uiState.value.transientMessage).isEqualTo(Res.string.provider_keys_save_success)
        }

    @Test
    fun sequential_emitTransientMessage_then_consume_compose_correctly() =
        runTest(testDispatcher) {
            setEndpoints(
                mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)),
            )
            val vm = newVm()
            advanceUntilIdle()

            vm.emitTransientMessage(Res.string.provider_keys_save_success)
            vm.emitTransientMessage(Res.string.provider_keys_revoke_success)
            advanceUntilIdle()
            // The latest write wins; intermediate state is not lost in a way that resets to null.
            assertThat(vm.uiState.value.transientMessage).isEqualTo(Res.string.provider_keys_revoke_success)
            vm.consumeTransientMessage()
            assertThat(vm.uiState.value.transientMessage).isNull()
        }

    @Test
    fun onChildKeyChanged_forceFor_survives_cancellation_by_configs_collector() =
        runTest(testDispatcher) {
            // Race: onChildKeyChanged kicks off refresh(forceFor={openAI}) → mid-fetch the
            // endpointConfigs flow emits a new value → collector triggers refresh() with
            // forceFor=emptySet, cancelling the in-flight job. Without merge-on-cancel, the
            // restarted refresh would skip openAI (no force, openAI already in previousByName)
            // and the just-mutated row falls back to its cached pre-save value. With merge,
            // openAI stays in pendingForceFor and is re-fetched after the restart.

            val initialMap = mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true))
            val flow = MutableStateFlow(initialMap)
            coEvery { configRepository.endpointConfigs } returns flow
            coEvery { configRepository.fetchEndpoints() } returns Result.Success(initialMap)
            coEvery { configRepository.fetchModels() } returns Result.Success(emptyMap())

            // Init load completes synchronously (Unset returned without suspension).
            val vm = newVm()
            advanceUntilIdle()
            // Sanity: initial row exists, fetched once.
            coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

            // Now arrange a suspending fetchKeyState so we can interleave a config emission
            // while onChildKeyChanged's refresh is mid-flight. The first call after this
            // re-stub suspends until we complete the deferred.
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { keyRepository.fetchKeyState("openAI") } coAnswers {
                calls++
                if (calls == 1) {
                    // Block the in-flight call so we can emit a new config value mid-fan-out.
                    gate.await()
                }
                Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))
            }

            // Kick off the explicit refresh (forceFor={openAI}).
            vm.onChildKeyChanged("openAI")
            // Let the launched coroutine reach the suspended fetchKeyState before we race.
            advanceUntilIdle()

            // Mid-flight: emit a differing config map. The configs collector calls refresh()
            // with forceFor=emptySet, which cancels the in-flight (suspended) job.
            flow.value = initialMap + ("openAI" to EndpointConfig(
                name = "openAI",
                userProvide = true,
                modelDisplayLabel = "OpenAI (admin updated)",
            ))
            advanceUntilIdle()

            // Release the gate so any still-active call can resume. Must be safe to release
            // even though the original call was cancelled — CompletableDeferred ignores extra
            // completions.
            gate.complete(Unit)
            advanceUntilIdle()

            // openAI MUST have been re-fetched (initial load + post-cancel restart). The merge
            // semantics carry the explicit forceFor across cancellation.
            coVerify(atLeast = 2) { keyRepository.fetchKeyState("openAI") }
            // And the row must reflect the fresh Set(neverExpires) state, not the pre-save
            // cached Unset.
            val resolved = vm.uiState.value.entries.single { it.endpointName == "openAI" }.keyState
            assertThat(resolved).isInstanceOf(KeyState.Set::class.java)
        }

    @Test
    fun a_revoke_shows_on_the_row_at_once_and_a_failed_reread_keeps_it() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(STORED_KEY)
        val vm = newVm()
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(STORED_KEY)
        // The config reload is slow and the re-read after it fails.
        val endpointsGate = CompletableDeferred<Unit>()
        coEvery { configRepository.fetchEndpoints() } coAnswers {
            endpointsGate.await()
            Result.Success(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        }
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")

        vm.onChildKeyChanged("openAI", KeyState.Unset)
        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(KeyState.Unset)
        endpointsGate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        val row = vm.uiState.value.entries.single()
        assertThat(row.keyState).isEqualTo(KeyState.Unset)
        assertThat(row.fetchFailed).isFalse()
    }

    @Test
    fun a_save_shows_the_saved_key_and_a_failed_reread_keeps_it() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        val vm = newVm()
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(KeyState.Unset)
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")

        vm.onChildKeyChanged("openAI", STORED_KEY)
        advanceUntilIdle()

        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(STORED_KEY)
    }

    @Test
    fun a_mutation_with_no_known_state_reads_couldnt_check_when_the_reread_fails() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        val vm = newVm()
        advanceUntilIdle()
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")

        vm.onChildKeyChanged("openAI")
        advanceUntilIdle()

        val row = vm.uiState.value.entries.single()
        assertThat(row.keyState).isEqualTo(KeyState.Loading)
        assertThat(row.fetchFailed).isTrue()
    }

    @Test
    fun a_reread_in_flight_before_a_revoke_cannot_bring_the_old_key_back() = runTest(testDispatcher) {
        setEndpoints(mapOf("openAI" to EndpointConfig(name = "openAI", userProvide = true)))
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(STORED_KEY)
        val vm = newVm()
        advanceUntilIdle()
        vm.refreshOnReturn() // the initial load's resume
        // A resume re-read goes out before the revoke and answers with the pre-revoke key.
        val staleGate = CompletableDeferred<Unit>()
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers {
            staleGate.await()
            Result.Success(STORED_KEY)
        }
        vm.refreshOnReturn()
        advanceUntilIdle()

        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(message = "offline")
        // The config reload is slow (a real network), so the stale read answers before it.
        val configGate = CompletableDeferred<Unit>()
        coEvery { configRepository.fetchEndpoints() } coAnswers {
            configGate.await()
            Result.Success(configRepository.endpointConfigs.value)
        }
        vm.onChildKeyChanged("openAI", KeyState.Unset)
        staleGate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(KeyState.Unset)

        // The re-read after the reload fails, and keeps the known answer.
        configGate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.single().keyState).isEqualTo(KeyState.Unset)
    }

    @Test
    fun a_reread_in_flight_before_a_revoke_all_cannot_bring_the_old_keys_back() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
            ),
        )
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(STORED_KEY)
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)
        val vm = newVm()
        advanceUntilIdle()
        vm.refreshOnReturn() // the initial load's resume
        // A resume re-read goes out before the revoke and answers with the pre-revoke keys.
        val staleGate = CompletableDeferred<Unit>()
        coEvery { keyRepository.fetchKeyState(any()) } coAnswers {
            staleGate.await()
            Result.Success(STORED_KEY)
        }
        vm.refreshOnReturn()
        advanceUntilIdle()

        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Error(message = "offline")
        val configGate = CompletableDeferred<Unit>()
        coEvery { configRepository.fetchEndpoints() } coAnswers {
            configGate.await()
            Result.Success(configRepository.endpointConfigs.value)
        }
        vm.revokeAll()
        staleGate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.map { it.keyState }).containsExactly(KeyState.Unset, KeyState.Unset)

        configGate.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.entries.map { it.keyState }).containsExactly(KeyState.Unset, KeyState.Unset)
    }

    @Test
    fun revokeAll_clears_every_row_and_a_failed_reread_keeps_them_clear() = runTest(testDispatcher) {
        setEndpoints(
            mapOf(
                "openAI" to EndpointConfig(name = "openAI", userProvide = true),
                "google" to EndpointConfig(name = "google", userProvide = true),
            ),
        )
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(STORED_KEY)
        coEvery { keyRepository.deleteAllKeys() } returns Result.Success(Unit)
        val vm = newVm()
        advanceUntilIdle()
        val endpointsGate = CompletableDeferred<Unit>()
        coEvery { configRepository.fetchEndpoints() } coAnswers {
            endpointsGate.await()
            Result.Success(configRepository.endpointConfigs.value)
        }
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Error(message = "offline")

        vm.revokeAll()
        advanceUntilIdle()
        // Before the config reload answers, the rows already read as empty.
        assertThat(vm.uiState.value.entries.map { it.keyState }).containsExactly(KeyState.Unset, KeyState.Unset)
        endpointsGate.complete(Unit)
        advanceUntilIdle()

        assertThat(vm.uiState.value.entries.map { it.keyState }).containsExactly(KeyState.Unset, KeyState.Unset)
        assertThat(vm.uiState.value.entries.none { it.fetchFailed }).isTrue()
    }
}

private val STORED_KEY = KeyState.Set(
    expiresAt = Instant.parse("2099-01-01T00:00:00Z"),
    neverExpires = false,
    wire = "2099-01-01T00:00:00Z",
)

private val EXPIRED_KEY = KeyState.Expired(
    expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
    wire = "2026-01-01T00:00:00Z",
)
