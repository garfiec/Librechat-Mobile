package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyField
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
 * Which of the key and the base URL the OpenAI and custom forms ask for and require, per the
 * endpoint's `userProvide` / `userProvideURL` flags, and what a save sends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeyRequiredFieldsTest {

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

    private fun configFlow(
        name: String,
        type: String? = null,
        userProvideURL: Boolean = false,
        userProvide: Boolean = true,
    ) = MutableStateFlow(
        mapOf(name to EndpointConfig(name = name, type = type, userProvide = userProvide, userProvideURL = userProvideURL)),
    )

    @Test
    fun openai_base_url_only_saves_with_just_a_url() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            configFlow("openAI", userProvide = false, userProvideURL = true)

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        // The server reads the key from its own environment: the sheet asks for the URL alone.
        assertThat(vm.uiState.value.form.fields).containsExactly(ProviderKeyField.BASE_URL)
        vm.updateField(ProviderKeyField.BASE_URL, "https://proxy.example.com/v1")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed.keys).containsExactly("apiKey", "baseURL")
        assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("")
        assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("https://proxy.example.com/v1")
    }

    @Test
    fun openai_base_url_only_requires_the_url() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            configFlow("openAI", userProvide = false, userProvideURL = true)

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        assertThat(vm.uiState.value.missingFields).containsExactly(ProviderKeyField.BASE_URL)
    }

    @Test
    fun openai_key_only_still_requires_the_key() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns configFlow("openAI")

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        assertThat(vm.uiState.value.form.fields).containsExactly(ProviderKeyField.API_KEY)
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        assertThat(vm.uiState.value.missingFields).containsExactly(ProviderKeyField.API_KEY)
    }

    @Test
    fun openai_with_both_flags_requires_the_key_and_keeps_the_url_optional() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            configFlow("openAI", userProvide = true, userProvideURL = true)

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        assertThat(vm.uiState.value.form.fields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL).inOrder()
        vm.updateField(ProviderKeyField.BASE_URL, "https://proxy.example.com/v1")
        vm.save()
        advanceUntilIdle()
        assertThat(capturedRequests).isEmpty()
        assertThat(vm.uiState.value.missingFields).containsExactly(ProviderKeyField.API_KEY)

        vm.updateField(ProviderKeyField.API_KEY, "sk-secret")
        vm.updateField(ProviderKeyField.BASE_URL, "")
        vm.save()
        advanceUntilIdle()
        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-secret")
        assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
    }

    @Test
    fun custom_base_url_only_still_requires_the_key() = runTest(testDispatcher) {
        // The custom endpoint reads the user's key whenever it reads any user value.
        coEvery { configRepository.endpointConfigs } returns
            configFlow("Gateway", type = "custom", userProvide = false, userProvideURL = true)

        val vm = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        assertThat(vm.uiState.value.form.fields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL).inOrder()
        vm.updateField(ProviderKeyField.BASE_URL, "https://gw.example.com/v1")
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        assertThat(vm.uiState.value.missingFields).containsExactly(ProviderKeyField.API_KEY)
    }

    @Test
    fun custom_with_both_flags_requires_both() = runTest(testDispatcher) {
        coEvery { configRepository.endpointConfigs } returns
            configFlow("Gateway", type = "custom", userProvide = true, userProvideURL = true)

        val vm = SetProviderKeyViewModel("Gateway", keyRepository, configRepository, SavedStateHandle())
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
        assertThat(vm.uiState.value.missingFields)
            .containsExactly(ProviderKeyField.API_KEY, ProviderKeyField.BASE_URL)
    }
}
