package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_api_key_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_version
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_deployment
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_instance
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_access_key_id
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_secret_access_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_service_key_or_gemini
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormKind
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Wire-shape tests for the per-endpoint payload builder.
 *
 * Each test exercises one endpoint variant, builds the payload via `save()`, and asserts
 * the captured `UpdateKeyRequest.value` against the upstream wire contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetProviderKeyViewModelTest {

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

    private fun configFlow(name: String, type: String? = null, azure: Boolean? = null,
                          userProvideURL: Boolean = false, userProvide: Boolean = true) =
        MutableStateFlow(
            mapOf(
                name to EndpointConfig(
                    name = name,
                    type = type,
                    azure = azure,
                    userProvide = userProvide,
                    userProvideURL = userProvideURL,
                ),
            ),
        )

    @Test
    fun openai_payload_is_apiKey_plus_baseURL_pair() = runTest(testDispatcher) {
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-secret")
        vm.save()
        advanceUntilIdle()

        val req = capturedRequests.single()
        assertThat(req.name).isEqualTo("openAI")
        val parsed = Json.parseToJsonElement(req.value).jsonObject
        assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-secret")
        assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
    }

    @Test
    fun custom_with_userProvideURL_requires_baseURL() = runTest(testDispatcher) {
        val flow = configFlow("anthropic-custom", type = "custom", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("anthropic-custom", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-secret")
        // Skip baseURL — should reject.
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
    }

    @Test
    fun custom_with_userProvideURL_and_baseURL_succeeds() = runTest(testDispatcher) {
        val flow = configFlow("anthropic-custom", type = "custom", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("anthropic-custom", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-secret")
        vm.updateBaseUrl("https://example.com/v1")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-secret")
        assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("https://example.com/v1")
    }

    @Test
    fun azure_quad_wraps_inner_json_in_apiKey_field() = runTest(testDispatcher) {
        val flow = configFlow("azureOpenAI", type = "azureOpenAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.API_KEY, "azkey")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.INSTANCE, "myinst")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.DEPLOYMENT, "gpt4")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.VERSION, "2024-02-15-preview")
        vm.save()
        advanceUntilIdle()

        val req = capturedRequests.single()
        // The "n/a" literal must NEVER appear in the saved payload (it's a UI placeholder for
        // disabled fields when the form is in a non-Azure variant; the union-typed form means
        // it should never reach the wire).
        assertThat(req.value).doesNotContain("n/a")

        val outer = Json.parseToJsonElement(req.value).jsonObject
        assertThat(outer["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
        val innerStr = outer["apiKey"]?.jsonPrimitive?.content!!
        val inner = Json.parseToJsonElement(innerStr).jsonObject
        assertThat(inner["azureOpenAIApiKey"]?.jsonPrimitive?.content).isEqualTo("azkey")
        assertThat(inner["azureOpenAIApiInstanceName"]?.jsonPrimitive?.content).isEqualTo("myinst")
        assertThat(inner["azureOpenAIApiDeploymentName"]?.jsonPrimitive?.content).isEqualTo("gpt4")
        assertThat(inner["azureOpenAIApiVersion"]?.jsonPrimitive?.content)
            .isEqualTo("2024-02-15-preview")
    }

    @Test
    fun azure_missing_field_rejects() = runTest(testDispatcher) {
        val flow = configFlow("azureOpenAI", type = "azureOpenAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.API_KEY, "azkey")
        // Skip the other three.
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
    }

    @Test
    fun azure_flagged_endpoint_remaps_keyName_to_azureOpenAI() = runTest(testDispatcher) {
        val flow = configFlow("my-azure", type = "openAI", azure = true)
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("my-azure", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.API_KEY, "k")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.INSTANCE, "i")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.DEPLOYMENT, "d")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.VERSION, "v")
        vm.save()
        advanceUntilIdle()

        // azure==true remaps the key name to "azureOpenAI" regardless of display name.
        assertThat(capturedRequests.single().name).isEqualTo("azureOpenAI")
    }

    @Test
    fun bedrock_omits_sessionToken_when_blank() = runTest(testDispatcher) {
        val flow = configFlow("bedrock", type = "bedrock")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.ACCESS_KEY_ID, "AKID")
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.SECRET_ACCESS_KEY, "SECRET")
        // sessionToken left blank
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed.keys).containsExactly("accessKeyId", "secretAccessKey")
        assertThat(parsed["accessKeyId"]?.jsonPrimitive?.content).isEqualTo("AKID")
        assertThat(parsed["secretAccessKey"]?.jsonPrimitive?.content).isEqualTo("SECRET")
    }

    @Test
    fun bedrock_includes_sessionToken_when_provided() = runTest(testDispatcher) {
        val flow = configFlow("bedrock", type = "bedrock")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.ACCESS_KEY_ID, "AKID")
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.SECRET_ACCESS_KEY, "SECRET")
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.SESSION_TOKEN, "TOKEN")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed.keys).containsExactly("accessKeyId", "secretAccessKey", "sessionToken")
        assertThat(parsed["sessionToken"]?.jsonPrimitive?.content).isEqualTo("TOKEN")
    }

    @Test
    fun bedrock_missing_required_rejects() = runTest(testDispatcher) {
        val flow = configFlow("bedrock", type = "bedrock")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.ACCESS_KEY_ID, "AKID")
        // missing secretAccessKey
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
    }

    @Test
    fun google_both_fields_populated_emits_both_keys() = runTest(testDispatcher) {
        val flow = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        advanceUntilIdle()
        // Service-key validation requires a long-enough private_key — we pass an inner string
        // here without validation by sending it directly; field-level validation only blocks
        // the import success indicator, not the save() path.
        vm.updateGoogleServiceKey("""{"client_email":"x@y.com"}""")
        vm.updateGoogleApiKey("gemini-key")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        // Both keys are always present (mobile divergence from web — backend tolerates `""`).
        assertThat(parsed.keys).containsExactly("GOOGLE_SERVICE_KEY", "GOOGLE_API_KEY")
        // GOOGLE_SERVICE_KEY is a JSON string (the stringified inner JSON), NOT a JSON object.
        assertThat(parsed["GOOGLE_SERVICE_KEY"]?.jsonPrimitive?.content)
            .isEqualTo("""{"client_email":"x@y.com"}""")
        assertThat(parsed["GOOGLE_API_KEY"]?.jsonPrimitive?.content).isEqualTo("gemini-key")
    }

    @Test
    fun google_only_service_key_emits_empty_api_key() = runTest(testDispatcher) {
        val flow = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateGoogleServiceKey("""{"client_email":"x@y.com"}""")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed["GOOGLE_API_KEY"]?.jsonPrimitive?.content).isEqualTo("")
    }

    @Test
    fun google_only_api_key_emits_empty_service_key() = runTest(testDispatcher) {
        val flow = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateGoogleApiKey("gemini-key")
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed["GOOGLE_SERVICE_KEY"]?.jsonPrimitive?.content).isEqualTo("")
        assertThat(parsed["GOOGLE_API_KEY"]?.jsonPrimitive?.content).isEqualTo("gemini-key")
    }

    @Test
    fun google_both_empty_rejects() = runTest(testDispatcher) {
        val flow = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests).isEmpty()
    }

    @Test
    fun other_endpoint_sends_plain_key_string() = runTest(testDispatcher) {
        val flow = configFlow("anthropic", type = "anthropic")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("anthropic", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-ant-xxx")
        vm.save()
        advanceUntilIdle()

        // Plain string, not a JSON object.
        assertThat(capturedRequests.single().value).isEqualTo("sk-ant-xxx")
    }

    @Test
    fun never_expiry_sends_empty_string() = runTest(testDispatcher) {
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-x")
        vm.selectExpiry(ProviderKeyExpiry.NEVER)
        vm.save()
        advanceUntilIdle()

        assertThat(capturedRequests.single().expiresAt).isEqualTo("")
    }

    @Test
    fun default_expiry_is_12h() {
        // Static check: enum default in initial state.
        // (Build a VM with no config map — the default expiry is part of `SetProviderKeyUiState`.)
        val flow = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        coEvery { configRepository.endpointConfigs } returns flow
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        assertThat(vm.uiState.value.expiry).isEqualTo(ProviderKeyExpiry.TWELVE_HOURS)
    }

    @Test
    fun resolveFormKind_routes_each_variant() {
        assertThat(
            SetProviderKeyViewModel.resolveFormKind("openAI", EndpointConfig(name = "openAI")),
        ).isEqualTo(ProviderKeyFormKind.OPENAI)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "azureOpenAI",
                EndpointConfig(name = "azureOpenAI"),
            ),
        ).isEqualTo(ProviderKeyFormKind.AZURE)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind("google", EndpointConfig(name = "google")),
        ).isEqualTo(ProviderKeyFormKind.GOOGLE)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind("bedrock", EndpointConfig(name = "bedrock")),
        ).isEqualTo(ProviderKeyFormKind.BEDROCK)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "x",
                EndpointConfig(name = "x", type = "custom"),
            ),
        ).isEqualTo(ProviderKeyFormKind.CUSTOM)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "anthropic",
                EndpointConfig(name = "anthropic"),
            ),
        ).isEqualTo(ProviderKeyFormKind.OTHER)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "x",
                EndpointConfig(name = "x", azure = true),
            ),
        ).isEqualTo(ProviderKeyFormKind.AZURE)
    }

    @Test
    fun azureAssistants_without_azure_flag_routes_to_OPENAI() {
        // Per Option A: mirror web's known bug at upstream SetKeyDialog.tsx:215 (strict
        // `isAzure = endpoint === EModelEndpoint.azureOpenAI`, no `azureAssistants`).
        // Result on web: azureAssistants gets the plain OpenAI single-input form.
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "azureAssistants",
                EndpointConfig(name = "azureAssistants"),
            ),
        ).isEqualTo(ProviderKeyFormKind.OPENAI)
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "x",
                EndpointConfig(name = "x", type = "azureAssistants"),
            ),
        ).isEqualTo(ProviderKeyFormKind.OPENAI)
    }

    @Test
    fun azureAssistants_with_azure_flag_still_routes_to_AZURE() {
        // The `config.azure == true` short-circuit wins regardless of name/type — this is
        // how Azure-backed custom endpoints reach the quad-field form.
        assertThat(
            SetProviderKeyViewModel.resolveFormKind(
                "azureAssistants",
                EndpointConfig(name = "azureAssistants", azure = true),
            ),
        ).isEqualTo(ProviderKeyFormKind.AZURE)
    }

    @Test
    fun openAI_with_userProvideURL_and_blank_baseURL_succeeds() = runTest(testDispatcher) {
        // Web's submit validation at SetKeyDialog.tsx:226 skips baseURL for `isOpenAIBase`
        // endpoints regardless of `userProvideURL`. Mobile mirrors that: baseURL is
        // rendered-but-optional for openAI even when `userProvideURL=true`.
        val flow = configFlow("openAI", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-secret")
        // Intentionally skip baseURL.
        vm.save()
        advanceUntilIdle()

        val parsed = Json.parseToJsonElement(capturedRequests.single().value).jsonObject
        assertThat(parsed["apiKey"]?.jsonPrimitive?.content).isEqualTo("sk-secret")
        assertThat(parsed["baseURL"]?.jsonPrimitive?.content).isEqualTo("")
    }

    @Test
    fun resolveFormKind_uses_endpointName_when_type_and_name_both_null() {
        // Real `/api/endpoints` responses carry the endpoint name in the map key, NOT in
        // `EndpointConfig.name` (empirically null). Without falling back to the map-key
        // endpointName, built-in endpoints would route to OTHER.
        val config = EndpointConfig(name = null, type = null)
        assertThat(SetProviderKeyViewModel.resolveFormKind("openAI", config))
            .isEqualTo(ProviderKeyFormKind.OPENAI)
        assertThat(SetProviderKeyViewModel.resolveFormKind("google", config))
            .isEqualTo(ProviderKeyFormKind.GOOGLE)
        assertThat(SetProviderKeyViewModel.resolveFormKind("custom", config))
            .isEqualTo(ProviderKeyFormKind.CUSTOM)
        assertThat(SetProviderKeyViewModel.resolveFormKind("bedrock", config))
            .isEqualTo(ProviderKeyFormKind.BEDROCK)
        assertThat(SetProviderKeyViewModel.resolveFormKind("unknownProvider", config))
            .isEqualTo(ProviderKeyFormKind.OTHER)
    }

    // ---- Google validator must not crash on adversarial JSON ---------------------------

    @Test
    fun google_validator_rejects_array_for_client_email_without_crash() {
        val flow = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        coEvery { configRepository.endpointConfigs } returns flow
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        // Arrays/objects in primitive slots must NOT throw IllegalArgumentException via
        // `JsonElement.jsonPrimitive` extension. Safe-cast to JsonPrimitive should kick in.
        val malformed = """{"client_email": ["x@y.com"], "project_id": "p", "private_key": "..."}"""
        assertThat(vm.validateGoogleServiceKey(malformed))
            .isEqualTo(SetProviderKeyViewModel.ServiceKeyValidation.Invalid)
    }

    @Test
    fun google_validator_rejects_object_for_project_id_without_crash() {
        val flow = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        coEvery { configRepository.endpointConfigs } returns flow
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        val malformed = """{"client_email": "x@y.com", "project_id": {}, "private_key": "..."}"""
        assertThat(vm.validateGoogleServiceKey(malformed))
            .isEqualTo(SetProviderKeyViewModel.ServiceKeyValidation.Invalid)
    }

    @Test
    fun google_validator_rejects_array_for_private_key_without_crash() {
        val flow = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        coEvery { configRepository.endpointConfigs } returns flow
        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        val malformed = """{"client_email": "x@y.com", "project_id": "p", "private_key": [1,2]}"""
        assertThat(vm.validateGoogleServiceKey(malformed))
            .isEqualTo(SetProviderKeyViewModel.ServiceKeyValidation.Invalid)
    }

    // ---- RequiredFieldsMissing carries StringResource list -----------------------------

    /**
     * Subscribe to `vm.effects` BEFORE invoking the action under test.
     *
     * The effects flow is backed by a buffered Channel via `receiveAsFlow()` — emissions
     * before a collector attaches are buffered, so even if subscription is slightly late
     * the events still arrive. We attach the collector on an UnconfinedTestDispatcher so
     * its first suspension runs synchronously (collector live before this fn returns),
     * which keeps the assertion ordering deterministic.
     */
    private fun TestScope.subscribeEffects(
        vm: SetProviderKeyViewModel,
    ): MutableList<SetProviderKeyEffect> {
        val collected = mutableListOf<SetProviderKeyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { collected += it }
        }
        return collected
    }

    @Test
    fun openai_missing_apiKey_emits_localized_field_resource() = runTest(testDispatcher) {
        val flow = configFlow("openAI", userProvideURL = true)
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        // Per web parity (Gap 2): for `isOpenAIBase` endpoints, baseURL is rendered-but-
        // optional even when `userProvideURL=true` (SetKeyDialog.tsx:226 skips it). Only
        // apiKey is required for OpenAI; submitting both blank reports only apiKey missing.
        vm.save()
        advanceUntilIdle()

        val event = collected.filterIsInstance<SetProviderKeyEffect.RequiredFieldsMissing>().single()
        assertThat(event.fields).containsExactly(
            Res.string.provider_keys_field_api_key_label,
        )
    }

    @Test
    fun azure_missing_emits_azure_field_resources() = runTest(testDispatcher) {
        val flow = configFlow("azureOpenAI", type = "azureOpenAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.save()
        advanceUntilIdle()

        val event = collected.filterIsInstance<SetProviderKeyEffect.RequiredFieldsMissing>().single()
        assertThat(event.fields).containsExactly(
            Res.string.provider_keys_field_azure_api_key,
            Res.string.provider_keys_field_azure_instance,
            Res.string.provider_keys_field_azure_deployment,
            Res.string.provider_keys_field_azure_api_version,
        ).inOrder()
    }

    @Test
    fun bedrock_missing_emits_bedrock_field_resources() = runTest(testDispatcher) {
        val flow = configFlow("bedrock", type = "bedrock")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.save()
        advanceUntilIdle()

        val event = collected.filterIsInstance<SetProviderKeyEffect.RequiredFieldsMissing>().single()
        assertThat(event.fields).containsExactly(
            Res.string.provider_keys_field_bedrock_access_key_id,
            Res.string.provider_keys_field_bedrock_secret_access_key,
        ).inOrder()
    }

    @Test
    fun google_both_empty_emits_combined_label_resource() = runTest(testDispatcher) {
        val flow = configFlow("google", type = "google")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("google", keyRepository, configRepository)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.save()
        advanceUntilIdle()

        val event = collected.filterIsInstance<SetProviderKeyEffect.RequiredFieldsMissing>().single()
        assertThat(event.fields).containsExactly(
            Res.string.provider_keys_field_google_service_key_or_gemini,
        )
    }

    @Test
    fun other_missing_apiKey_emits_field_resource() = runTest(testDispatcher) {
        val flow = configFlow("anthropic", type = "anthropic")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("anthropic", keyRepository, configRepository)
        advanceUntilIdle()
        val collected = subscribeEffects(vm)

        vm.save()
        advanceUntilIdle()

        val event = collected.filterIsInstance<SetProviderKeyEffect.RequiredFieldsMissing>().single()
        assertThat(event.fields).containsExactly(Res.string.provider_keys_field_api_key_label)
    }

    // ---- Banner initial state -----------------------------------------------------------

    @Test
    fun initial_currentKeyState_is_Loading() {
        // The banner defaults to Loading until the host dialog's LaunchedEffect fires
        // refreshKeyState(), so it never briefly renders a stale Set(...) carried over.
        val flow = MutableStateFlow<Map<String, EndpointConfig>>(emptyMap())
        coEvery { configRepository.endpointConfigs } returns flow
        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        // Synchronous read: refreshKeyState hasn't been invoked yet.
        assertThat(vm.uiState.value.currentKeyState).isEqualTo(KeyState.Loading)
    }

    @Test
    fun currentKeyState_resolves_to_Unset_when_get_returns_null() = runTest(testDispatcher) {
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        // The host dialog's LaunchedEffect drives refreshKeyState — exercise it directly.
        vm.refreshKeyState()
        advanceUntilIdle()

        assertThat(vm.uiState.value.currentKeyState).isEqualTo(KeyState.Unset)
    }

    @Test
    fun refreshKeyState_picks_up_latest_expiry_on_reused_vm() = runTest(testDispatcher) {
        // The host dialog's `LaunchedEffect(endpointName)` calls `refreshKeyState()` on each
        // open. This test exercises that contract directly: two back-to-back opens see
        // distinct GET results (first Unset, then Set(neverExpires=true)).
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.fetchKeyState(any()) } returnsMany listOf(
            Result.Success(KeyState.Unset),
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")),
        )

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        // First open: LaunchedEffect fires refreshKeyState().
        vm.refreshKeyState()
        advanceUntilIdle()
        assertThat(vm.uiState.value.currentKeyState).isEqualTo(KeyState.Unset)

        // Reopen: LaunchedEffect fires refreshKeyState() again on the reused VM.
        vm.refreshKeyState()
        advanceUntilIdle()
        val resolved = vm.uiState.value.currentKeyState
        assertThat(resolved).isInstanceOf(KeyState.Set::class.java)
        assertThat((resolved as KeyState.Set).neverExpires).isTrue()
    }

    @Test
    fun revoke_resets_form_to_blank_for_OpenAi() = runTest(testDispatcher) {
        val flow = configFlow("openAI", userProvideURL = false)
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-typed-but-not-yet-saved")
        // Sanity: field is populated.
        assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey)
            .isEqualTo("sk-typed-but-not-yet-saved")

        vm.revoke()
        advanceUntilIdle()

        val form = vm.uiState.value.form
        assertThat(form).isInstanceOf(ProviderKeyFormState.ApiKeyAndOptionalBaseUrl::class.java)
        assertThat((form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEmpty()
        assertThat(form.baseURL).isEmpty()
        assertThat(vm.uiState.value.currentKeyState).isEqualTo(KeyState.Unset)
    }

    @Test
    fun revoke_resets_form_to_blank_for_Azure() = runTest(testDispatcher) {
        val flow = configFlow("azureOpenAI", type = "azureOpenAI")
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)

        val vm = SetProviderKeyViewModel("azureOpenAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.API_KEY, "azkey")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.INSTANCE, "inst")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.DEPLOYMENT, "dep")
        vm.updateAzureField(SetProviderKeyViewModel.AzureField.VERSION, "v1")

        vm.revoke()
        advanceUntilIdle()

        val form = vm.uiState.value.form as ProviderKeyFormState.Azure
        assertThat(form.azureOpenAIApiKey).isEmpty()
        assertThat(form.azureOpenAIApiInstanceName).isEmpty()
        assertThat(form.azureOpenAIApiDeploymentName).isEmpty()
        assertThat(form.azureOpenAIApiVersion).isEmpty()
    }

    @Test
    fun revoke_resets_form_to_blank_for_Bedrock() = runTest(testDispatcher) {
        val flow = configFlow("bedrock", type = "bedrock")
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.deleteKey(any()) } returns Result.Success(Unit)

        val vm = SetProviderKeyViewModel("bedrock", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.ACCESS_KEY_ID, "AKID")
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.SECRET_ACCESS_KEY, "SECRET")
        vm.updateBedrockField(SetProviderKeyViewModel.BedrockField.SESSION_TOKEN, "TOKEN")

        vm.revoke()
        advanceUntilIdle()

        val form = vm.uiState.value.form as ProviderKeyFormState.Bedrock
        assertThat(form.accessKeyId).isEmpty()
        assertThat(form.secretAccessKey).isEmpty()
        assertThat(form.sessionToken).isEmpty()
    }

    @Test
    fun revoke_does_not_reset_form_on_failure() = runTest(testDispatcher) {
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow
        coEvery { keyRepository.deleteKey(any()) } returns Result.Error(message = "network")

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-x")
        vm.revoke()
        advanceUntilIdle()

        // Failed revoke leaves user-typed input intact so they can retry without re-typing.
        assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEqualTo("sk-x")
    }

    @Test
    fun save_success_resets_form_to_blank() = runTest(testDispatcher) {
        // After save success the dialog auto-dismisses, but the keyed VM persists. Subsequent
        // re-open should not show preloaded user-typed values.
        val flow = configFlow("openAI")
        coEvery { configRepository.endpointConfigs } returns flow

        val vm = SetProviderKeyViewModel("openAI", keyRepository, configRepository)
        advanceUntilIdle()
        vm.updateApiKey("sk-x")
        vm.save()
        advanceUntilIdle()

        assertThat((vm.uiState.value.form as ProviderKeyFormState.ApiKeyAndOptionalBaseUrl).apiKey).isEmpty()
    }
}
