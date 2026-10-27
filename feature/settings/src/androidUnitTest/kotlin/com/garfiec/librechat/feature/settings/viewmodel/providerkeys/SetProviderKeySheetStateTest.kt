package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_invalid
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_failed
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.ServiceKeyValidation
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.garfiec.librechat.feature.settings.state.providerkeys.validateGoogleServiceKey
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The sheet's lifecycle and validation: the draft in saved state (secrets included), reset only on
 * an explicit open, no result replayed across opens, Save and Revoke excluding each other, missing
 * fields as state, and the Google and Bedrock rules ported from web.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeySheetStateTest {

    private val testDispatcher = StandardTestDispatcher()

    private val keyRepository = mockk<KeyRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)

    private val capturedRequests = mutableListOf<UpdateKeyRequest>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
        val capture = slot<UpdateKeyRequest>()
        coEvery { keyRepository.updateKey(capture(capture)) } answers {
            capturedRequests.add(capture.captured)
            Result.Success(Unit)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        capturedRequests.clear()
    }

    private fun configFlow(name: String, type: String? = null, userProvideURL: Boolean = false) =
        MutableStateFlow(mapOf(name to EndpointConfig(name = name, type = type, userProvide = true, userProvideURL = userProvideURL)))

    private fun TestScope.subscribeEffects(vm: SetProviderKeyViewModel): MutableList<SetProviderKeyEffect> {
        val collected = mutableListOf<SetProviderKeyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { collected += it }
        }
        return collected
    }

    // ---- Saved state: the draft survives process death -----------------------------------

    /** What the system hands a recreated process: the same keys and values, in a new handle. */
    private fun SavedStateHandle.restored(): SavedStateHandle =
        SavedStateHandle(keys().associateWith<String, Any?> { this[it] })

    private fun bedrockFlow(
        accessKeyId: Boolean? = null,
        secretAccessKey: Boolean? = null,
        sessionToken: Boolean? = null,
        bearerToken: Boolean? = null,
    ) = MutableStateFlow(
        mapOf(
            "bedrock" to EndpointConfig(
                userProvide = true,
                userProvideAccessKeyId = accessKeyId,
                userProvideSecretAccessKey = secretAccessKey,
                userProvideSessionToken = sessionToken,
                userProvideBearerToken = bearerToken,
            ),
        ),
    )

    private val neverExpiring = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")

    @Test
    fun draft_and_expiry_round_trip_through_saved_state_including_secrets() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("custom-x", type = "custom", userProvideURL = true)
        val handle = SavedStateHandle()
        val vm = SetProviderKeyViewModel("custom-x", keyRepository, configRepository, handle)
        vm.onOpened(1, KeyState.Unset)
        vm.updateField(ProviderKeyField.API_KEY, "sk-secret")
        vm.updateField(ProviderKeyField.BASE_URL, "https://llm.example.com/v1")
        vm.selectExpiry(ProviderKeyExpiry.ONE_WEEK)
        advanceUntilIdle()

        val restored = SetProviderKeyViewModel("custom-x", keyRepository, configRepository, handle.restored())
        val form = restored.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
        assertThat(form.apiKey).isEqualTo("sk-secret")
        assertThat(form.baseURL).isEqualTo("https://llm.example.com/v1")
        // Same config on both sides; a config that changed meanwhile is SetProviderKeyConfigChangeRestoreTest.
        assertThat(form.userProvideURL).isTrue()
        assertThat(restored.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.ONE_WEEK)
    }

    @Test
    fun every_form_kind_round_trips_through_saved_state() = runTest(testDispatcher) {
        val azureHandle = SavedStateHandle()
        coEvery { configRepository.endpointConfigs } returns configFlow("azureOpenAI", type = "azureOpenAI")
        SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository, azureHandle).apply {
            updateField(ProviderKeyField.AZURE_API_KEY, "az-secret")
            updateField(ProviderKeyField.AZURE_INSTANCE, "inst")
            updateField(ProviderKeyField.AZURE_DEPLOYMENT, "dep")
            updateField(ProviderKeyField.AZURE_VERSION, "2024-06-01")
        }
        val azure = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository, azureHandle.restored())
            .uiState.value.form
        assertThat(azure).isEqualTo(
            ProviderKeyFormState.Azure("az-secret", "inst", "dep", "2024-06-01"),
        )

        val googleHandle = SavedStateHandle()
        coEvery { configRepository.endpointConfigs } returns configFlow("google", type = "google")
        SetProviderKeyViewModel("google", keyRepository, configRepository, googleHandle).apply {
            updateField(ProviderKeyField.GOOGLE_SERVICE_KEY, VALID_SERVICE_KEY)
            updateField(ProviderKeyField.GOOGLE_API_KEY, "gemini-secret")
        }
        val google = SetProviderKeyViewModel("google", keyRepository, configRepository, googleHandle.restored())
            .uiState.value.form as ProviderKeyFormState.Google
        assertThat(google.serviceKeyJson).isEqualTo(VALID_SERVICE_KEY)
        assertThat(google.geminiApiKey).isEqualTo("gemini-secret")
        assertThat(google.serviceKeyImportSuccess).isTrue()

        val bedrockHandle = SavedStateHandle()
        coEvery { configRepository.endpointConfigs } returns bedrockFlow(accessKeyId = true, secretAccessKey = true, bearerToken = true)
        SetProviderKeyViewModel("bedrock", keyRepository, configRepository, bedrockHandle).apply {
            updateField(ProviderKeyField.BEDROCK_ACCESS_KEY_ID, "AKID")
            updateField(ProviderKeyField.BEDROCK_BEARER_TOKEN, "bearer-secret")
        }
        val bedrock = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, bedrockHandle.restored())
            .uiState.value.form as ProviderKeyFormState.Bedrock
        assertThat(bedrock.accessKeyId).isEqualTo("AKID")
        assertThat(bedrock.bearerToken).isEqualTo("bearer-secret")

        val otherHandle = SavedStateHandle()
        coEvery { configRepository.endpointConfigs } returns configFlow("anthropic", type = "anthropic")
        SetProviderKeyViewModel("anthropic", keyRepository, configRepository, otherHandle).updateField(ProviderKeyField.API_KEY, "sk-ant")
        assertThat(
            SetProviderKeyViewModel("anthropic", keyRepository, configRepository, otherHandle.restored())
                .uiState.value.form,
        ).isEqualTo(ProviderKeyFormState.Other("sk-ant"))
    }

    @Test
    fun restored_draft_wins_over_the_same_open_request() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val handle = SavedStateHandle()
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle)
        vm.onOpened(7, KeyState.Unset)
        vm.updateField(ProviderKeyField.API_KEY, "sk-draft")
        advanceUntilIdle()

        // Process death, then the restored sheet composes and reports the same open request.
        val restored = SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle.restored())
        restored.onOpened(7, KeyState.Loading)
        advanceUntilIdle()

        assertThat((restored.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey)
            .isEqualTo("sk-draft")
    }

    @Test
    fun a_new_open_request_resets_the_form_and_the_same_one_does_not() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        vm.save() // flags the empty key
        vm.updateField(ProviderKeyField.BASE_URL, "https://x")
        vm.selectExpiry(ProviderKeyExpiry.NEVER)

        // Recomposition / resume: same request.
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        val kept = vm.uiState.value
        assertThat((kept.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).baseURL).isEqualTo("https://x")
        assertThat(kept.expiry).isEqualTo(ProviderKeyExpiry.NEVER)
        assertThat(kept.missingFields).containsExactly(ProviderKeyField.API_KEY)

        // Explicit reopen: blank form, default expiry, nothing flagged.
        vm.onOpened(2, KeyState.Unset)
        advanceUntilIdle()
        val reset = vm.uiState.value
        assertThat((reset.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).baseURL).isEmpty()
        assertThat(reset.expiry).isEqualTo(ProviderKeyExpiry.TWELVE_HOURS)
        assertThat(reset.missingFields).isEmpty()
    }

    @Test
    fun a_never_expiring_row_opens_on_never_and_the_sheet_reads_nothing_itself() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())

        vm.onOpened(1, neverExpiring)
        advanceUntilIdle()

        assertThat(vm.uiState.value.rowKeyState).isEqualTo(neverExpiring)
        assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.NEVER)
        // The list owns the key state; the sheet only shows the row's.
        coVerify(exactly = 0) { keyRepository.fetchKeyState(any()) }
    }

    @Test
    fun the_expiry_follows_a_row_answer_that_lands_after_the_open_until_the_user_picks_one() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
            val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
            vm.onOpened(1, KeyState.Loading)
            assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.TWELVE_HOURS)

            vm.onRowKeyState(neverExpiring, fetchFailed = false)
            assertThat(vm.uiState.value.rowKeyState).isEqualTo(neverExpiring)
            assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.NEVER)

            vm.selectExpiry(ProviderKeyExpiry.ONE_DAY)
            vm.onRowKeyState(KeyState.Unset, fetchFailed = false)
            assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.ONE_DAY)
        }

    @Test
    fun expiring_key_opens_on_the_default_expiry() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val expiring = KeyState.Set(
            expiresAt = kotlin.time.Instant.parse("2099-01-01T00:00:00Z"),
            neverExpires = false,
            wire = "2099-01-01T00:00:00Z",
        )
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(expiring)
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, expiring)
        advanceUntilIdle()
        assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.TWELVE_HOURS)
    }

    @Test
    fun results_from_a_previous_open_are_not_replayed() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        coEvery { keyRepository.updateKey(any()) } returns Result.Error(message = "boom")
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, neverExpiring)
        vm.updateField(ProviderKeyField.API_KEY, "sk-x")
        vm.save() // fails: said in the sheet's state, nothing queued
        advanceUntilIdle()
        vm.revoke() // succeeds: its result waits, and would close the next sheet if acted on
        advanceUntilIdle()
        assertThat(vm.uiState.value.pendingResult).isNotNull()

        vm.onOpened(2, KeyState.Unset)
        val collected = subscribeEffects(vm)
        advanceUntilIdle()

        assertThat(collected).isEmpty()
        assertThat(vm.uiState.value.pendingResult).isNull()
    }

    @Test
    fun save_result_arrives_while_the_sheet_is_still_busy_and_never_closes_a_later_open() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
            val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
            vm.onOpened(1, KeyState.Unset)
            advanceUntilIdle()
            val collected = subscribeEffects(vm)
            vm.updateField(ProviderKeyField.API_KEY, "sk-new")
            vm.save()
            advanceUntilIdle()

            // The result is out, tagged with the open it belongs to.
            val result = checkNotNull(vm.uiState.value.pendingResult)
            assertThat(result.openRequest).isEqualTo(1L)
            // The sheet acts on it and closes.
            assertThat(vm.onMutationResult(result, currentOpenRequest = 1)).isTrue()
            assertThat(vm.uiState.value.isBusy).isFalse()

            // The user taps the row again.
            vm.onOpened(2, KeyState.Unset)
            vm.updateField(ProviderKeyField.API_KEY, "sk-typed-in-the-new-sheet")
            advanceUntilIdle()

            // Nothing more arrives to close the new sheet, and its input stays.
            assertThat(vm.uiState.value.pendingResult).isNull()
            assertThat(collected).isEmpty()
            assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey)
                .isEqualTo("sk-typed-in-the-new-sheet")
        }

    @Test
    fun a_row_that_could_not_be_checked_is_unknown_in_the_sheet_until_its_retry_answers() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
            val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())

            vm.onOpened(1, KeyState.Loading, rowFetchFailed = true)
            advanceUntilIdle()

            // "Couldn't check", not "Not set", and Revoke stays offered for a key that may exist.
            val state = vm.uiState.value
            assertThat(state.rowKeyState).isEqualTo(KeyState.Loading)
            assertThat(state.rowFetchFailed).isTrue()
            assertThat(state.canRevoke).isTrue()

            // The list's retry answers.
            vm.onRowKeyState(neverExpiring, fetchFailed = false)
            assertThat(vm.uiState.value.rowKeyState).isEqualTo(neverExpiring)
            assertThat(vm.uiState.value.rowFetchFailed).isFalse()
            assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.NEVER)
        }

    @Test
    fun a_failed_save_is_said_in_the_sheet_and_clears_on_the_next_edit() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        coEvery { keyRepository.updateKey(any()) } returns Result.Error(message = "offline")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        val collected = subscribeEffects(vm)
        vm.updateField(ProviderKeyField.API_KEY, "sk-x")
        vm.save()
        advanceUntilIdle()

        // Inside the sheet, not a list snackbar the open sheet would cover.
        assertThat(vm.uiState.value.submitError).isEqualTo(Res.string.provider_keys_save_failed)
        assertThat(collected).isEmpty()
        assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEqualTo("sk-x")

        vm.updateField(ProviderKeyField.API_KEY, "sk-xy")
        assertThat(vm.uiState.value.submitError).isNull()

        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.submitError).isEqualTo(Res.string.provider_keys_save_failed)
        coEvery { keyRepository.updateKey(any()) } returns Result.Success(Unit)
        vm.save()
        // Cleared as soon as the retry goes out.
        assertThat(vm.uiState.value.submitError).isNull()
        advanceUntilIdle()
        assertThat(vm.uiState.value.submitError).isNull()
    }

    @Test
    fun a_failed_revoke_is_said_in_the_sheet_and_a_new_open_clears_it() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        coEvery { keyRepository.deleteKey(any()) } returns Result.Error(message = "offline")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, neverExpiring)
        val collected = subscribeEffects(vm)
        vm.revoke()
        advanceUntilIdle()

        assertThat(vm.uiState.value.submitError).isEqualTo(Res.string.provider_keys_revoke_failed)
        assertThat(collected).isEmpty()

        vm.onOpened(2, neverExpiring)
        assertThat(vm.uiState.value.submitError).isNull()
    }

    @Test
    fun save_and_revoke_exclude_each_other() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val saveGate = CompletableDeferred<Unit>()
        coEvery { keyRepository.updateKey(any()) } coAnswers {
            saveGate.await()
            Result.Success(Unit)
        }
        val revokeGate = CompletableDeferred<Unit>()
        coEvery { keyRepository.deleteKey(any()) } coAnswers {
            revokeGate.await()
            Result.Success(Unit)
        }
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.updateField(ProviderKeyField.API_KEY, "sk-x")

        vm.save()
        vm.save()
        vm.revoke()
        assertThat(vm.uiState.value.isBusy).isTrue()
        advanceUntilIdle()
        saveGate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { keyRepository.updateKey(any()) }
        coVerify(exactly = 0) { keyRepository.deleteKey(any()) }
        // Busy until the sheet has acted on the save's result.
        assertThat(vm.uiState.value.isBusy).isTrue()
        assertThat(vm.onMutationResult(checkNotNull(vm.uiState.value.pendingResult), currentOpenRequest = 1)).isTrue()
        assertThat(vm.uiState.value.isBusy).isFalse()

        vm.revoke()
        vm.updateField(ProviderKeyField.API_KEY, "sk-y")
        vm.save()
        advanceUntilIdle()
        revokeGate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { keyRepository.deleteKey(any()) }
        coVerify(exactly = 1) { keyRepository.updateKey(any()) }
    }

    @Test
    fun missing_fields_are_state_and_clear_on_edit() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("azureOpenAI", type = "azureOpenAI")
        val vm = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository, SavedStateHandle())
        vm.updateField(ProviderKeyField.AZURE_INSTANCE, "inst")
        vm.save()

        assertThat(vm.uiState.value.missingFields).containsExactly(
            ProviderKeyField.AZURE_API_KEY,
            ProviderKeyField.AZURE_DEPLOYMENT,
            ProviderKeyField.AZURE_VERSION,
        )

        vm.updateField(ProviderKeyField.AZURE_API_KEY, "k")
        assertThat(vm.uiState.value.missingFields).containsExactly(
            ProviderKeyField.AZURE_DEPLOYMENT,
            ProviderKeyField.AZURE_VERSION,
        )
    }

    @Test
    fun save_success_clears_the_saved_draft() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val handle = SavedStateHandle()
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle)
        vm.updateField(ProviderKeyField.API_KEY, "sk-submitted")
        vm.save()
        advanceUntilIdle()

        val restored = SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle.restored())
        assertThat((restored.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEmpty()
    }

    // ---- Google: web's CredentialsSchema ---------------------------------------------------

    @Test
    fun invalid_service_key_blocks_save() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("google", type = "google")
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository, SavedStateHandle())
        val collected = subscribeEffects(vm)
        vm.updateField(ProviderKeyField.GOOGLE_SERVICE_KEY, """{"client_email":"x@y.com"}""")
        vm.updateField(ProviderKeyField.GOOGLE_API_KEY, "gemini-key")
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        assertThat(collected).containsExactly(
            SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid),
        )
    }

    @Test
    fun google_validator_matches_web_schema_bounds() {
        fun key(email: String = "svc@project.iam.gserviceaccount.com", project: String = "my-project", privateKeyLength: Int = 601) =
            Json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("client_email", email)
                    put("project_id", project)
                    put("private_key", "k".repeat(privateKeyLength))
                },
            )
        val valid = ServiceKeyValidation.Valid
        val invalid = ServiceKeyValidation.Invalid
        assertThat(validateGoogleServiceKey(key())).isEqualTo(valid)
        assertThat(validateGoogleServiceKey(key(privateKeyLength = 600))).isEqualTo(invalid)
        assertThat(validateGoogleServiceKey(key(project = "ab"))).isEqualTo(invalid)
        assertThat(validateGoogleServiceKey(key(email = "not-an-email@"))).isEqualTo(invalid)
        assertThat(validateGoogleServiceKey(key(email = "a@b"))).isEqualTo(invalid)
        assertThat(validateGoogleServiceKey(key(email = "first..last@example.com"))).isEqualTo(invalid)
    }

    @Test
    fun unreadable_service_key_file_shows_the_invalid_message() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("google", type = "google")
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository, SavedStateHandle())
        val collected = subscribeEffects(vm)
        vm.onGoogleServiceKeyFileRead(null)
        advanceUntilIdle()

        assertThat((vm.uiState.value.form as ProviderKeyFormState.Google).hasServiceKeyImportError).isTrue()
        assertThat(collected).containsExactly(
            SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid),
        )
    }

    // ---- Bedrock: fields per server flag, web's wire shape ----------------------------------

    private fun bedrockApiKey(value: String): JsonObject {
        val outer = Json.parseToJsonElement(value).jsonObject
        assertThat(outer.keys).containsExactly("apiKey", "baseURL")
        assertThat(outer["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
        return Json.parseToJsonElement(outer.getValue("apiKey").jsonPrimitive.content).jsonObject
    }

    @Test
    fun bedrock_form_follows_the_server_flags() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns bedrockFlow(bearerToken = true)
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        val form = vm.uiState.value.form as ProviderKeyFormState.Bedrock
        assertThat(form.userProvideBearerToken).isTrue()
        assertThat(form.asksForAccessKeys).isFalse()
        assertThat(form.legacyCredentials).isFalse()
    }

    @Test
    fun bedrock_bearer_token_alone_is_sent_in_webs_envelope() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            bedrockFlow(accessKeyId = true, secretAccessKey = true, sessionToken = true, bearerToken = true)
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        vm.updateField(ProviderKeyField.BEDROCK_BEARER_TOKEN, "  tok-123  ")
        vm.save()
        advanceUntilIdle()

        val request = capturedRequests.single()
        assertThat(request.name).isEqualTo("bedrock")
        val credentials = bedrockApiKey(request.value)
        assertThat(credentials.keys).containsExactly("bearerToken")
        assertThat(credentials["bearerToken"]?.jsonPrimitive?.content).isEqualTo("tok-123")
    }

    @Test
    fun bedrock_flagged_access_keys_are_all_required_without_a_bearer_token() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            bedrockFlow(accessKeyId = true, secretAccessKey = true, sessionToken = true, bearerToken = true)
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        vm.updateField(ProviderKeyField.BEDROCK_ACCESS_KEY_ID, "AKID")
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        // The bearer token isn't required while access keys are on offer; a flagged session token is.
        assertThat(vm.uiState.value.missingFields).containsExactly(
            ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY,
            ProviderKeyField.BEDROCK_SESSION_TOKEN,
        )

        vm.updateField(ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY, "SECRET")
        vm.updateField(ProviderKeyField.BEDROCK_SESSION_TOKEN, "SESSION")
        vm.save()
        advanceUntilIdle()

        val credentials = bedrockApiKey(capturedRequests.single().value)
        assertThat(credentials.keys).containsExactly("accessKeyId", "secretAccessKey", "sessionToken")
        assertThat(credentials["secretAccessKey"]?.jsonPrimitive?.content).isEqualTo("SECRET")
    }

    @Test
    fun bedrock_bearer_only_server_requires_the_bearer_token() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns bedrockFlow(bearerToken = true)
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        val collected = subscribeEffects(vm)
        vm.save()
        advanceUntilIdle()

        assertThat(vm.uiState.value.missingFields).containsExactly(ProviderKeyField.BEDROCK_BEARER_TOKEN)
        assertThat(collected).containsExactly(SetProviderKeyEffect.FocusField(ProviderKeyField.BEDROCK_BEARER_TOKEN))
    }

    @Test
    fun bedrock_typing_a_bearer_token_clears_every_flagged_field() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            bedrockFlow(accessKeyId = true, secretAccessKey = true, bearerToken = true)
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        vm.save()
        assertThat(vm.uiState.value.missingFields).isNotEmpty()
        vm.updateField(ProviderKeyField.BEDROCK_BEARER_TOKEN, "tok")
        assertThat(vm.uiState.value.missingFields).isEmpty()
    }

    @Test
    fun bedrock_without_server_flags_keeps_the_flat_trio() = runTest(testDispatcher) {
        // A server older than the per-field flags: access key ID + secret, optional session token.
        coEvery { configRepository.endpointConfigs } returns bedrockFlow()
        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, SavedStateHandle())
        val form = vm.uiState.value.form as ProviderKeyFormState.Bedrock
        assertThat(form.legacyCredentials).isTrue()
        assertThat(form.userProvideBearerToken).isFalse()
    }

    // ---- What a mutation leaves: the result carries it, a failed re-read keeps it ---------

    @Test
    fun a_save_reports_the_saved_key() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.updateField(ProviderKeyField.API_KEY, "sk-new")
        vm.selectExpiry(ProviderKeyExpiry.NEVER)
        vm.save()
        advanceUntilIdle()

        assertThat(collected).isEmpty()
        assertThat(vm.uiState.value.pendingResult?.keyState).isEqualTo(neverExpiring)
        assertThat(vm.uiState.value.rowKeyState).isEqualTo(neverExpiring)
    }

    @Test
    fun a_save_with_an_expiry_reports_a_key_that_expires() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.updateField(ProviderKeyField.API_KEY, "sk-new")
        vm.selectExpiry(ProviderKeyExpiry.ONE_DAY)
        vm.save()
        advanceUntilIdle()

        assertThat(collected).isEmpty()
        val saved = vm.uiState.value.pendingResult?.keyState
        assertThat(saved).isInstanceOf(KeyState.Set::class.java)
        saved as KeyState.Set
        assertThat(saved.neverExpires).isFalse()
        assertThat(saved.wire).isEqualTo(capturedRequests.single().expiresAt)
    }

    @Test
    fun a_revoke_reports_no_key() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, neverExpiring)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.revoke()
        advanceUntilIdle()

        assertThat(collected).isEmpty()
        assertThat(vm.uiState.value.pendingResult?.keyState).isEqualTo(KeyState.Unset)
    }
}
