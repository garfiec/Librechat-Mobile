package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The sheet must survive the user switching apps to fetch a key. Driven at the
 * ViewModel boundary the screen wires: the list VM's open endpoint + open request, and the sheet VM
 * the screen hands that request to on every composition ([SetProviderKeyViewModel.onOpened]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderKeySheetLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()
    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
    }
    private val configs =
        mapOf("custom-x" to EndpointConfig(type = "custom", userProvide = true, userProvideURL = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
        every { configRepository.startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        every { configRepository.endpointConfigs } returns MutableStateFlow(configs)
        coEvery { configRepository.fetchEndpoints() } returns Result.Success(configs)
        coEvery { configRepository.fetchModels() } returns Result.Success(emptyMap())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun listVm(handle: SavedStateHandle) =
        ProviderKeysViewModel(keyRepository, configRepository, roleRepository, handle)

    private fun sheetVm(handle: SavedStateHandle) =
        SetProviderKeyViewModel("custom-x", keyRepository, configRepository, handle)

    /** What the system hands a recreated process: the same keys and values, in a new handle. */
    private fun SavedStateHandle.restored(): SavedStateHandle =
        SavedStateHandle(keys().associateWith<String, Any?> { this[it] })

    /** What the screen does on every composition of the open sheet: the row's state goes with it. */
    private fun SetProviderKeyViewModel.composedUnder(list: ProviderKeysViewModel) {
        val row = list.uiState.value.dialogEntry
        onOpened(list.uiState.value.dialogOpenRequest, row?.keyState ?: KeyState.Loading, row?.fetchFailed ?: false)
    }

    /** The user comes back from another app with the sheet open: ON_RESUME re-reads the rows. */
    private fun returnFromAnotherApp(list: ProviderKeysViewModel) {
        list.refreshOnReturn()
    }

    private val SetProviderKeyViewModel.draft: ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
        get() = uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl

    @Test
    fun resume_while_the_sheet_is_open_keeps_the_sheet_and_the_draft() = runTest(testDispatcher) {
        val list = listVm(SavedStateHandle())
        val sheet = sheetVm(SavedStateHandle())
        advanceUntilIdle()
        list.refreshOnReturn() // the initial load's resume
        list.openDialog("custom-x")
        sheet.composedUnder(list)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-half-pasted")
        sheet.selectExpiry(ProviderKeyExpiry.ONE_WEEK)
        advanceUntilIdle()

        // Away to copy the base URL, then back: ON_RESUME re-reads the rows, and the sheet recomposes.
        returnFromAnotherApp(list)
        advanceUntilIdle()
        sheet.composedUnder(list)
        advanceUntilIdle()

        assertThat(list.uiState.value.pendingDialogEndpoint).isEqualTo("custom-x")
        assertThat(sheet.draft.apiKey).isEqualTo("sk-half-pasted")
        assertThat(sheet.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.ONE_WEEK)
        assertThat(sheet.uiState.value.isDirty).isTrue()
    }

    @Test
    fun a_key_state_change_seen_on_resume_keeps_the_draft() = runTest(testDispatcher) {
        val list = listVm(SavedStateHandle())
        val sheet = sheetVm(SavedStateHandle())
        advanceUntilIdle()
        list.refreshOnReturn() // the initial load's resume
        list.openDialog("custom-x")
        sheet.composedUnder(list)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-draft")
        advanceUntilIdle()
        assertThat(sheet.uiState.value.rowKeyState).isEqualTo(KeyState.Unset)

        // The key was set on the web meanwhile: the row, and the header with it, pick it up; the draft stays.
        val setOnWeb = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(setOnWeb)
        returnFromAnotherApp(list)
        advanceUntilIdle()
        sheet.composedUnder(list)
        advanceUntilIdle()

        assertThat(list.uiState.value.entries.single().keyState).isEqualTo(setOnWeb)
        assertThat(sheet.uiState.value.rowKeyState).isEqualTo(setOnWeb)
        assertThat(sheet.draft.apiKey).isEqualTo("sk-draft")
    }

    @Test
    fun process_death_brings_the_sheet_back_with_the_draft_masked_secrets_included() = runTest(testDispatcher) {
        val listHandle = SavedStateHandle()
        val sheetHandle = SavedStateHandle()
        val list = listVm(listHandle)
        val sheet = sheetVm(sheetHandle)
        advanceUntilIdle()
        list.openDialog("custom-x")
        sheet.composedUnder(list)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-secret")
        sheet.updateField(ProviderKeyField.BASE_URL, "https://llm.example.com/v1")
        advanceUntilIdle()

        val restoredList = listVm(listHandle.restored())
        val restoredSheet = sheetVm(sheetHandle.restored())
        restoredSheet.composedUnder(restoredList)
        advanceUntilIdle()

        assertThat(restoredList.uiState.value.pendingDialogEndpoint).isEqualTo("custom-x")
        assertThat(restoredSheet.draft.apiKey).isEqualTo("sk-secret")
        assertThat(restoredSheet.draft.baseURL).isEqualTo("https://llm.example.com/v1")
    }

    @Test
    fun process_death_before_the_configs_reload_still_brings_the_draft_back() = runTest(testDispatcher) {
        val listHandle = SavedStateHandle()
        val sheetHandle = SavedStateHandle()
        val list = listVm(listHandle)
        val sheet = sheetVm(sheetHandle)
        advanceUntilIdle()
        list.openDialog("custom-x")
        sheet.composedUnder(list)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-secret")
        sheet.updateField(ProviderKeyField.BASE_URL, "https://llm.example.com/v1")
        advanceUntilIdle()

        // A new process: the repository's in-memory map is empty until it reloads, and the
        // restored sheet composes in the first frame, before that.
        val reloading = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { configRepository.endpointConfigs } returns reloading
        val restoredList = listVm(listHandle.restored())
        val restoredSheet = sheetVm(sheetHandle.restored())
        restoredSheet.composedUnder(restoredList)
        advanceUntilIdle()
        assertThat(restoredSheet.uiState.value.awaitingConfig).isTrue()

        reloading.value = configs
        advanceUntilIdle()
        restoredSheet.composedUnder(restoredList)
        advanceUntilIdle()

        assertThat(restoredList.uiState.value.pendingDialogEndpoint).isEqualTo("custom-x")
        assertThat(restoredSheet.uiState.value.awaitingConfig).isFalse()
        assertThat(restoredSheet.draft.apiKey).isEqualTo("sk-secret")
        assertThat(restoredSheet.draft.baseURL).isEqualTo("https://llm.example.com/v1")
        assertThat(restoredSheet.draft.userProvideURL).isTrue()
    }

    @Test
    fun discarding_before_the_configs_reload_clears_the_saved_draft() = runTest(testDispatcher) {
        val sheetHandle = SavedStateHandle()
        val sheet = sheetVm(sheetHandle)
        sheet.onOpened(1, KeyState.Unset)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-discard-me")
        advanceUntilIdle()

        val reloading = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        every { configRepository.endpointConfigs } returns reloading
        val restoredHandle = sheetHandle.restored()
        val restored = sheetVm(restoredHandle)
        assertThat(restored.uiState.value.isDirty).isTrue()
        restored.discardDraft()
        assertThat(restored.uiState.value.isDirty).isFalse()
        reloading.value = configs
        advanceUntilIdle()

        assertThat(restored.draft.apiKey).isEmpty()
        assertThat(sheetVm(restoredHandle.restored()).draft.apiKey).isEmpty()
    }

    @Test
    fun an_explicit_reopen_starts_blank() = runTest(testDispatcher) {
        val list = listVm(SavedStateHandle())
        val sheet = sheetVm(SavedStateHandle())
        advanceUntilIdle()
        list.openDialog("custom-x")
        sheet.composedUnder(list)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-old")
        list.dismissDialog()

        list.openDialog("custom-x")
        sheet.composedUnder(list)
        advanceUntilIdle()

        assertThat(sheet.draft.apiKey).isEmpty()
        assertThat(sheet.uiState.value.isDirty).isFalse()
    }

    @Test
    fun discarding_clears_the_saved_draft() = runTest(testDispatcher) {
        val sheetHandle = SavedStateHandle()
        val sheet = sheetVm(sheetHandle)
        sheet.onOpened(1, KeyState.Unset)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-discard-me")
        sheet.selectExpiry(ProviderKeyExpiry.NEVER)

        sheet.discardDraft()

        assertThat(sheet.uiState.value.isDirty).isFalse()
        assertThat(sheet.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.TWELVE_HOURS)
        assertThat(sheetVm(sheetHandle.restored()).draft.apiKey).isEmpty()
    }

    @Test
    fun discard_is_refused_while_a_save_is_in_flight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { keyRepository.updateKey(any()) } coAnswers {
            gate.await()
            Result.Success(Unit)
        }
        val sheet = sheetVm(SavedStateHandle())
        sheet.onOpened(1, KeyState.Unset)
        sheet.updateField(ProviderKeyField.API_KEY, "sk-x")
        sheet.updateField(ProviderKeyField.BASE_URL, "https://x")
        sheet.save()
        assertThat(sheet.uiState.value.isBusy).isTrue()

        sheet.discardDraft()
        assertThat(sheet.draft.apiKey).isEqualTo("sk-x")
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun a_restored_untouched_sheet_is_not_dirty() = runTest(testDispatcher) {
        val sheetHandle = SavedStateHandle()
        val sheet = sheetVm(sheetHandle)
        sheet.onOpened(1, KeyState.Unset)
        sheet.selectExpiry(ProviderKeyExpiry.ONE_DAY)
        advanceUntilIdle()

        // An expiry pick alone isn't worth a discard prompt.
        val restored = sheetVm(sheetHandle.restored())
        restored.onOpened(1, KeyState.Unset)
        assertThat(restored.uiState.value.isDirty).isFalse()
    }

    @Test
    fun a_deep_link_to_an_endpoint_the_list_leaves_out_still_gives_the_header_a_key_state() =
        runTest(testDispatcher) {
            // "gone" isn't listed (not even in the configs), so no row holds its state.
            coEvery { keyRepository.fetchKeyState("gone") } returns Result.Error(message = "offline")
            val handle = SavedStateHandle()
            listVm(handle).openDialog("gone")
            // Restored before the first load: the load reads it with the rows.
            val list = listVm(handle.restored())
            val sheet = SetProviderKeyViewModel("gone", keyRepository, configRepository, SavedStateHandle())
            advanceUntilIdle()
            sheet.composedUnder(list)

            assertThat(list.uiState.value.entries.map { it.endpointName }).containsExactly("custom-x")
            assertThat(sheet.uiState.value.rowFetchFailed).isTrue()

            // The header's Retry is the list's.
            coEvery { keyRepository.fetchKeyState("gone") } returns Result.Success(KeyState.Unset)
            list.retry("gone")
            advanceUntilIdle()
            sheet.composedUnder(list)
            assertThat(sheet.uiState.value.rowKeyState).isEqualTo(KeyState.Unset)

            // A save lands on it as on a row, and a failed re-read keeps that answer.
            val saved = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")
            coEvery { keyRepository.fetchKeyState("gone") } returns Result.Error(message = "offline")
            list.onChildKeyChanged("gone", saved)
            advanceUntilIdle()
            assertThat(list.uiState.value.dialogEntry?.keyState).isEqualTo(saved)

            list.dismissDialog()
            assertThat(list.uiState.value.unlistedDialogEntry).isNull()
        }
}
