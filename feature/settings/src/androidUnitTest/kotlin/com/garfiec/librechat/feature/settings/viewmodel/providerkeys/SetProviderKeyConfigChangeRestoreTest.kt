package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A draft saved under one endpoint config and restored under a changed one (the admin edited the
 * server while the process was dead): the typed values come back, but what the config decides is
 * read fresh, and a draft of a form kind the endpoint no longer has is dropped, from the saved
 * state too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeyConfigChangeRestoreTest {

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

    /** What the system hands a recreated process: the same keys and values, in a new handle. */
    private fun SavedStateHandle.restored(): SavedStateHandle =
        SavedStateHandle(keys().associateWith<String, Any?> { this[it] })

    private fun SavedStateHandle.anyValueContains(text: String): Boolean =
        keys().any { key ->
            val value: Any? = this[key]
            value?.toString()?.contains(text) == true
        }

    private fun configs(name: String, config: EndpointConfig) = MutableStateFlow(mapOf(name to config))

    private fun bedrock(bearerToken: Boolean?) = configs(
        "bedrock",
        EndpointConfig(
            userProvide = true,
            userProvideAccessKeyId = true,
            userProvideSecretAccessKey = true,
            userProvideBearerToken = bearerToken,
        ),
    )

    @Test
    fun custom_draft_restored_after_the_base_url_became_required_asks_for_it() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            configs("Gateway", EndpointConfig(type = "custom", userProvide = true, userProvideURL = false))
        val handle = SavedStateHandle()
        SetProviderKeyViewModel("Gateway", keyRepository, configRepository, handle).apply {
            onOpened(1, KeyState.Unset)
            updateField(ProviderKeyField.API_KEY, "sk-gw-secret")
        }
        advanceUntilIdle()

        coEvery { configRepository.endpointConfigs } returns
            configs("Gateway", EndpointConfig(type = "custom", userProvide = true, userProvideURL = true))
        val restored = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, handle.restored())
        restored.onOpened(1, KeyState.Loading)
        advanceUntilIdle()

        val form = restored.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
        assertThat(form.apiKey).isEqualTo("sk-gw-secret")
        assertThat(form.userProvideURL).isTrue()

        restored.save()
        advanceUntilIdle()
        assertThat(capturedRequests).isEmpty()
        assertThat(restored.uiState.value.missingFields).containsExactly(ProviderKeyField.BASE_URL)
    }

    @Test
    fun bedrock_draft_restored_after_the_bearer_flag_was_dropped_keeps_only_the_access_keys() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns bedrock(bearerToken = true)
            val handle = SavedStateHandle()
            SetProviderKeyViewModel("bedrock", keyRepository, configRepository, handle).apply {
                onOpened(1, KeyState.Unset)
                updateField(ProviderKeyField.BEDROCK_ACCESS_KEY_ID, "AKID")
                updateField(ProviderKeyField.BEDROCK_SECRET_ACCESS_KEY, "secret-key")
                updateField(ProviderKeyField.BEDROCK_BEARER_TOKEN, "bearer-secret")
            }
            advanceUntilIdle()

            coEvery { configRepository.endpointConfigs } returns bedrock(bearerToken = null)
            val restoredHandle = handle.restored()
            val restored = SetProviderKeyViewModel("bedrock", keyRepository, configRepository, restoredHandle)
            restored.onOpened(1, KeyState.Loading)
            advanceUntilIdle()

            val form = restored.uiState.value.form as ProviderKeyFormState.Bedrock
            assertThat(form.userProvideBearerToken).isFalse()
            assertThat(form.userProvideAccessKeyId).isTrue()
            assertThat(form.userProvideSecretAccessKey).isTrue()
            assertThat(form.accessKeyId).isEqualTo("AKID")
            assertThat(form.secretAccessKey).isEqualTo("secret-key")
            // The token for the field that is gone isn't kept out of sight, in the form or the saved state.
            assertThat(form.bearerToken).isEmpty()
            assertThat(restoredHandle.anyValueContains("bearer-secret")).isFalse()

            // The server no longer reads a bearer token, so Save sends the access keys.
            restored.save()
            advanceUntilIdle()
            val outer = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
            val inner = Json.parseToJsonElement(outer.getValue("apiKey").jsonPrimitive.content).jsonObject
            assertThat(inner.keys).containsExactly("accessKeyId", "secretAccessKey")
            assertThat(inner["accessKeyId"]?.jsonPrimitive?.content).isEqualTo("AKID")
        }

    @Test
    fun azure_draft_restored_after_the_endpoint_became_custom_is_dropped_from_the_saved_state() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns
                configs("Gateway", EndpointConfig(type = "custom", azure = true, userProvide = true))
            val handle = SavedStateHandle()
            SetProviderKeyViewModel("Gateway", keyRepository, configRepository, handle).apply {
                onOpened(1, KeyState.Unset)
                updateField(ProviderKeyField.AZURE_API_KEY, "az-secret")
                updateField(ProviderKeyField.AZURE_INSTANCE, "inst")
            }
            advanceUntilIdle()
            assertThat(handle.anyValueContains("az-secret")).isTrue()

            // Restored before the configs reload; they arrive without `azure`.
            val reloaded = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
            coEvery { configRepository.endpointConfigs } returns reloaded
            val restoredHandle = handle.restored()
            val restored = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, restoredHandle)
            restored.onOpened(1, KeyState.Loading)
            advanceUntilIdle()
            reloaded.value = mapOf("Gateway" to EndpointConfig(type = "custom", userProvide = true))
            advanceUntilIdle()

            val state = restored.uiState.value
            assertThat(state.form).isEqualTo(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl())
            assertThat(state.isDirty).isFalse()
            // Dropped from the saved state as well, not only from the screen: another process
            // death before the next edit must not bring the secret back.
            assertThat(restoredHandle.anyValueContains("az-secret")).isFalse()
        }

    @Test
    fun openai_draft_restored_after_the_base_url_was_turned_off_drops_the_hidden_url() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns
                configs("openAI", EndpointConfig(userProvide = true, userProvideURL = true))
            val handle = SavedStateHandle()
            SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle).apply {
                onOpened(1, KeyState.Unset)
                updateField(ProviderKeyField.BASE_URL, "https://stale-proxy.example.com")
            }
            advanceUntilIdle()
            assertThat(handle.anyValueContains("stale-proxy")).isTrue()

            // Restored before the configs reload; they arrive without `userProvideURL`.
            val reloaded = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
            coEvery { configRepository.endpointConfigs } returns reloaded
            val restoredHandle = handle.restored()
            val restored = SetProviderKeyViewModel("openAI", keyRepository, configRepository, restoredHandle)
            restored.onOpened(1, KeyState.Loading)
            advanceUntilIdle()
            reloaded.value = mapOf("openAI" to EndpointConfig(userProvide = true))
            advanceUntilIdle()

            val state = restored.uiState.value
            val form = state.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
            assertThat(form.fields).containsExactly(ProviderKeyField.API_KEY)
            assertThat(form.baseURL).isEmpty()
            // Nothing the sheet shows was typed, so leaving it doesn't prompt.
            assertThat(state.isDirty).isFalse()
            assertThat(restoredHandle.anyValueContains("stale-proxy")).isFalse()

            restored.updateField(ProviderKeyField.API_KEY, "sk-now")
            restored.save()
            advanceUntilIdle()
            val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
            assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-now")
            assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
        }

    @Test
    fun openai_draft_restored_after_the_endpoint_became_base_url_only_drops_the_hidden_key() =
        runTest(testDispatcher) {
            coEvery { configRepository.endpointConfigs } returns
                configs("openAI", EndpointConfig(userProvide = true, userProvideURL = true))
            val handle = SavedStateHandle()
            SetProviderKeyViewModel("openAI", keyRepository, configRepository, handle).apply {
                onOpened(1, KeyState.Unset)
                updateField(ProviderKeyField.API_KEY, "sk-hidden-secret")
                updateField(ProviderKeyField.BASE_URL, "https://proxy.example.com")
            }
            advanceUntilIdle()

            val reloaded = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
            coEvery { configRepository.endpointConfigs } returns reloaded
            val restoredHandle = handle.restored()
            val restored = SetProviderKeyViewModel("openAI", keyRepository, configRepository, restoredHandle)
            restored.onOpened(1, KeyState.Loading)
            advanceUntilIdle()
            reloaded.value = mapOf("openAI" to EndpointConfig(userProvide = false, userProvideURL = true))
            advanceUntilIdle()

            val form = restored.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl
            assertThat(form.fields).containsExactly(ProviderKeyField.BASE_URL)
            assertThat(form.apiKey).isEmpty()
            assertThat(form.baseURL).isEqualTo("https://proxy.example.com")
            assertThat(restoredHandle.anyValueContains("sk-hidden-secret")).isFalse()

            restored.save()
            advanceUntilIdle()
            val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
            assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("")
            assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("https://proxy.example.com")
        }
}
