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
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyNeeds
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
 * A restored process rebuilds the sheet in its first frame, while the repository's in-memory config
 * map is still empty: the restored draft must wait for the configs, not settle on a guessed form,
 * and come back on the right one, with the right wire shape, once they load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeyRestoreTest {

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

    private fun bedrockFlow(accessKeyId: Boolean?, secretAccessKey: Boolean?, bearerToken: Boolean?) = MutableStateFlow(
        mapOf(
            "bedrock" to EndpointConfig(
                userProvide = true,
                userProvideAccessKeyId = accessKeyId,
                userProvideSecretAccessKey = secretAccessKey,
                userProvideBearerToken = bearerToken,
            ),
        ),
    )

    /** What the system hands a recreated process: the same keys and values, in a new handle. */
    private fun SavedStateHandle.restored(): SavedStateHandle =
        SavedStateHandle(keys().associateWith<String, Any?> { this[it] })

    /** The credentials inside web's Bedrock envelope. */
    private fun bedrockApiKey(value: String): JsonObject {
        val outer = Json.parseToJsonElement(value).jsonObject
        assertThat(outer.keys).containsExactly("apiKey", "baseURL")
        return Json.parseToJsonElement(outer.getValue("apiKey").jsonPrimitive.content).jsonObject
    }

    /** The configs a restored process starts with: nothing until the repository reloads them. */
    private fun notYetLoaded() = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())

    @Test
    fun custom_draft_restored_before_the_configs_load_comes_back_on_its_own_form() = runTest(testDispatcher) {
        val loaded = configFlow("OpenRouter", type = "custom", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns loaded
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, handle).apply {
            onOpened(3, KeyState.Unset)
            updateField(ProviderKeyField.API_KEY, "sk-or-secret")
            updateField(ProviderKeyField.BASE_URL, "https://openrouter.ai/api/v1")
        }
        advanceUntilIdle()

        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val restoredHandle = handle.restored()
        val restored = SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, restoredHandle)
        // The sheet composes and reports the same open; nothing typed or tapped lands yet.
        restored.onOpened(3, KeyState.Loading)
        restored.updateField(ProviderKeyField.API_KEY, "typed-before-the-form-was-shown")
        restored.save()
        advanceUntilIdle()
        assertThat(restored.uiState.value.awaitingConfig).isTrue()
        assertThat(restored.uiState.value.isDirty).isTrue()
        assertThat(capturedRequests).isEmpty()
        // Nothing has overwritten the saved draft while it waited.
        val savedAgain = SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, restoredHandle.restored())
            .uiState.value.heldDraft as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
        assertThat(savedAgain.apiKey).isEqualTo("sk-or-secret")
        assertThat(savedAgain.baseURL).isEqualTo("https://openrouter.ai/api/v1")

        configs.value = loaded.value
        advanceUntilIdle()

        val state = restored.uiState.value
        assertThat(state.awaitingConfig).isFalse()
        val form = state.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
        assertThat(form.apiKey).isEqualTo("sk-or-secret")
        assertThat(form.baseURL).isEqualTo("https://openrouter.ai/api/v1")
        assertThat(form.userProvideURL).isTrue()
        assertThat(form.isOpenAIBase).isFalse()

        restored.save()
        advanceUntilIdle()
        val payload = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(payload.keys).containsExactly("apiKey", "baseURL")
        assertThat(payload["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-or-secret")
        assertThat(payload["baseURL"]?.jsonPrimitive?.content).isEqualTo("https://openrouter.ai/api/v1")
    }

    @Test
    fun azure_flag_custom_draft_restored_before_the_configs_load_keeps_the_azure_form() = runTest(testDispatcher) {
        val loaded = MutableStateFlow(
            mapOf("Gateway" to EndpointConfig(type = "custom", azure = true, userProvide = true)),
        )
        coEvery { configRepository.endpointConfigs } returns loaded
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("Gateway", keyRepository, configRepository, handle).apply {
            onOpened(1, KeyState.Unset)
            updateField(ProviderKeyField.AZURE_API_KEY, "az-secret")
            updateField(ProviderKeyField.AZURE_INSTANCE, "inst")
            updateField(ProviderKeyField.AZURE_DEPLOYMENT, "dep")
            updateField(ProviderKeyField.AZURE_VERSION, "2024-06-01")
        }
        advanceUntilIdle()

        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val restored = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, handle.restored())
        restored.onOpened(1, KeyState.Loading)
        advanceUntilIdle()
        // The stored key's name depends on `azure`, so its state isn't read under the bare name.
        coVerify(exactly = 0) { keyRepository.fetchKeyState("Gateway") }

        configs.value = loaded.value
        advanceUntilIdle()

        assertThat(restored.uiState.value.form)
            .isEqualTo(ProviderKeyFormState.Azure("az-secret", "inst", "dep", "2024-06-01"))
        restored.save()
        advanceUntilIdle()
        val request = capturedRequests.last()
        assertThat(request.name).isEqualTo("azureOpenAI")
        val inner = Json.parseToJsonElement(
            Json.parseToJsonElement(request.value).jsonObject.getValue("apiKey").jsonPrimitive.content,
        ).jsonObject
        assertThat(inner["azureOpenAIApiKey"]?.jsonPrimitive?.content).isEqualTo("az-secret")
        assertThat(inner["azureOpenAIApiVersion"]?.jsonPrimitive?.content).isEqualTo("2024-06-01")
    }

    @Test
    fun bedrock_bearer_draft_restored_before_the_configs_load_keeps_the_flagged_form() = runTest(testDispatcher) {
        val loaded = bedrockFlow(accessKeyId = true, secretAccessKey = true, bearerToken = true)
        coEvery { configRepository.endpointConfigs } returns loaded
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("bedrock", keyRepository, configRepository, handle).apply {
            onOpened(1, KeyState.Unset)
            updateField(ProviderKeyField.BEDROCK_BEARER_TOKEN, "bearer-secret")
        }
        advanceUntilIdle()

        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val restored = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, handle.restored())
        restored.onOpened(1, KeyState.Loading)
        advanceUntilIdle()
        configs.value = loaded.value
        advanceUntilIdle()

        val form = restored.uiState.value.form as ProviderKeyFormState.Bedrock
        assertThat(form.legacyCredentials).isFalse()
        assertThat(form.userProvideBearerToken).isTrue()
        assertThat(form.userProvideAccessKeyId).isTrue()
        assertThat(form.userProvideSecretAccessKey).isTrue()
        assertThat(form.userProvideSessionToken).isFalse()
        assertThat(form.bearerToken).isEqualTo("bearer-secret")
        restored.save()
        advanceUntilIdle()
        assertThat(bedrockApiKey(capturedRequests.last().value))
            .isEqualTo(buildJsonObject { put("bearerToken", "bearer-secret") })
    }

    @Test
    fun the_header_says_what_a_multi_field_form_needs_once_the_config_is_known() = runTest(testDispatcher) {
        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val vm = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, SavedStateHandle())
        assertThat(vm.uiState.value.needs).isNull()

        configs.value = mapOf("Gateway" to EndpointConfig(type = "custom", azure = true, userProvide = true))
        advanceUntilIdle()
        assertThat(vm.uiState.value.needs).isEqualTo(ProviderKeyNeeds.AZURE)

        coEvery { configRepository.endpointConfigs } returns configFlow("OpenRouter", type = "custom", userProvideURL = true)
        val custom = SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, SavedStateHandle())
        assertThat(custom.uiState.value.needs).isEqualTo(ProviderKeyNeeds.KEY_AND_BASE_URL)
        custom.onOpened(1, KeyState.Unset)
        assertThat(custom.uiState.value.needs).isEqualTo(ProviderKeyNeeds.KEY_AND_BASE_URL)
    }

    @Test
    fun a_new_open_before_the_configs_load_drops_the_restored_draft() = runTest(testDispatcher) {
        val loaded = configFlow("OpenRouter", type = "custom", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns loaded
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, handle).apply {
            onOpened(1, KeyState.Unset)
            updateField(ProviderKeyField.API_KEY, "sk-old")
        }
        advanceUntilIdle()

        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val restored = SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, handle.restored())
        restored.onOpened(2, KeyState.Unset)
        assertThat(restored.uiState.value.isDirty).isFalse()
        configs.value = loaded.value
        advanceUntilIdle()

        assertThat(restored.uiState.value.form)
            .isEqualTo(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(userProvideURL = true))
    }

    /** A service key web's schema accepts: a valid email, a project id and a 601+ character key. */
    private val validServiceKey = buildJsonObject {
        put("client_email", "svc@project.iam.gserviceaccount.com")
        put("project_id", "my-project")
        put("private_key", "k".repeat(601))
    }.toString()

    @Test
    fun a_service_key_file_read_before_the_configs_load_lands_in_the_settled_form() = runTest(testDispatcher) {
        val loaded = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns loaded
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("google", keyRepository, configRepository, handle).apply {
            onOpened(4, KeyState.Unset)
            updateField(ProviderKeyField.GOOGLE_API_KEY, "gemini-secret")
        }
        advanceUntilIdle()

        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val restoredHandle = handle.restored()
        val restored = SetProviderKeyViewModel("google", keyRepository, configRepository, restoredHandle)
        restored.onOpened(4, KeyState.Loading)
        // The picker's pending result reaches the recreated process before the configs reload.
        restored.onGoogleServiceKeyFileRead(validServiceKey)
        advanceUntilIdle()
        assertThat(restored.uiState.value.awaitingConfig).isTrue()

        configs.value = loaded.value
        advanceUntilIdle()

        val form = restored.uiState.value.form as ProviderKeyFormState.Google
        assertThat(form.serviceKeyJson).isEqualTo(validServiceKey)
        assertThat(form.serviceKeyImportSuccess).isTrue()
        assertThat(form.hasServiceKeyImportError).isFalse()
        assertThat(form.geminiApiKey).isEqualTo("gemini-secret")
        // Written back to the saved state like any other edit.
        val savedAgain = SetProviderKeyViewModel("google", keyRepository, configRepository, restoredHandle.restored())
        assertThat(savedAgain.uiState.value.form).isEqualTo(form)
    }

    @Test
    fun an_unreadable_file_picked_before_the_configs_load_is_reported_once_they_do() = runTest(testDispatcher) {
        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository, SavedStateHandle())
        val collected = mutableListOf<SetProviderKeyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { collected += it } }
        vm.onOpened(1, KeyState.Unset)
        vm.onGoogleServiceKeyFileRead(null)
        advanceUntilIdle()
        assertThat(collected).isEmpty()

        configs.value = configFlow("google", type = "google").value
        advanceUntilIdle()

        val form = vm.uiState.value.form as ProviderKeyFormState.Google
        assertThat(form.hasServiceKeyImportError).isTrue()
        assertThat(collected).containsExactly(SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid))
    }

    @Test
    fun a_file_held_for_a_form_that_settles_as_another_kind_is_dropped() = runTest(testDispatcher) {
        val configs = notYetLoaded()
        coEvery { configRepository.endpointConfigs } returns configs
        val vm = SetProviderKeyViewModel("OpenRouter", keyRepository, configRepository, SavedStateHandle())
        vm.onOpened(1, KeyState.Unset)
        vm.onGoogleServiceKeyFileRead(validServiceKey)
        configs.value = configFlow("OpenRouter", type = "custom").value
        advanceUntilIdle()

        assertThat(vm.uiState.value.form).isEqualTo(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl())
        assertThat(vm.uiState.value.isDirty).isFalse()
    }
}
