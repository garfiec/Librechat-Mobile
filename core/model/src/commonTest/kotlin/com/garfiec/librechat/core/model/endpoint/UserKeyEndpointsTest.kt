package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cases ported from upstream `client/src/components/Nav/SettingsTabs/ProviderKeys/utils.spec.ts`
 * at e8f3be08. Web returns config order; here every list is in display order (order, then label),
 * which with every `order` equal to 0 is alphabetical.
 */
class UserKeyEndpointsTest {

    private fun cfg(
        userProvide: Boolean? = null,
        userProvideURL: Boolean? = null,
        userProvideSecretAccessKey: Boolean? = null,
        userProvideBearerToken: Boolean? = null,
        allowedProviders: List<String> = emptyList(),
        order: Int? = 0,
        label: String? = null,
    ) = EndpointConfig(
        order = order,
        modelDisplayLabel = label,
        userProvide = userProvide,
        userProvideURL = userProvideURL,
        userProvideSecretAccessKey = userProvideSecretAccessKey,
        userProvideBearerToken = userProvideBearerToken,
        allowedProviders = allowedProviders,
    )

    private fun spec(endpoint: String) = ModelSpec(name = endpoint, label = endpoint, preset = Preset(endpoint = endpoint))

    private val endpointsConfig = mapOf(
        "openAI" to cfg(userProvide = true),
        "anthropic" to cfg(userProvide = true),
        "google" to cfg(userProvide = false),
        "bedrock" to cfg(userProvideBearerToken = true),
        "agents" to cfg(allowedProviders = listOf("anthropic")),
    )

    @Test
    fun api_key_endpoint_takes_user_value() {
        assertTrue(cfg(userProvide = true).takesUserProvidedValue())
    }

    @Test
    fun bedrock_credential_endpoint_takes_user_value() {
        assertTrue(cfg(userProvideSecretAccessKey = true).takesUserProvidedValue())
    }

    @Test
    fun base_url_alone_is_kept_unlike_web() {
        // Web returns false here; mobile keeps the endpoint so its base URL can be set.
        assertTrue(cfg(userProvideURL = true).takesUserProvidedValue())
    }

    @Test
    fun nothing_user_provided_is_excluded() {
        assertFalse(cfg().takesUserProvidedValue())
        assertFalse(cfg(userProvide = false, userProvideURL = false).takesUserProvidedValue())
    }

    @Test
    fun empty_when_endpoints_have_not_loaded() {
        assertEquals(emptyList(), userKeyEndpoints(emptyMap(), modelSpecs = null, hasAgentAccess = true))
    }

    @Test
    fun every_user_provided_endpoint_when_no_model_specs() {
        assertEquals(
            listOf("anthropic", "bedrock", "openAI"),
            userKeyEndpoints(endpointsConfig, modelSpecs = null, hasAgentAccess = true),
        )
        // An empty spec list is the same as none.
        assertEquals(
            listOf("anthropic", "bedrock", "openAI"),
            userKeyEndpoints(endpointsConfig, ModelSpecs(list = emptyList()), hasAgentAccess = true),
        )
    }

    @Test
    fun limited_to_endpoints_referenced_by_model_specs() {
        assertEquals(
            listOf("openAI"),
            userKeyEndpoints(endpointsConfig, ModelSpecs(list = listOf(spec("openAI"))), hasAgentAccess = true),
        )
    }

    @Test
    fun added_endpoints_join_spec_endpoints() {
        val specs = ModelSpecs(list = listOf(spec("openAI")), addedEndpoints = listOf("bedrock"))
        assertEquals(listOf("bedrock", "openAI"), userKeyEndpoints(endpointsConfig, specs, hasAgentAccess = true))
    }

    @Test
    fun reachable_agents_expand_to_allowed_providers() {
        val specs = ModelSpecs(list = listOf(spec("openAI")), addedEndpoints = listOf("agents"))
        assertEquals(listOf("anthropic", "openAI"), userKeyEndpoints(endpointsConfig, specs, hasAgentAccess = true))
    }

    @Test
    fun unrestricted_agents_expand_to_every_provider() {
        val unrestricted = endpointsConfig + ("agents" to cfg())
        val specs = ModelSpecs(list = listOf(spec("openAI")), addedEndpoints = listOf("agents"))
        assertEquals(
            listOf("anthropic", "bedrock", "openAI"),
            userKeyEndpoints(unrestricted, specs, hasAgentAccess = true),
        )
    }

    @Test
    fun unrestricted_agents_never_expand_to_agents_or_assistants() {
        val configs = mapOf(
            "openAI" to cfg(userProvide = true),
            "assistants" to cfg(userProvide = true),
            "azureAssistants" to cfg(userProvide = true),
            "agents" to cfg(userProvide = true),
        )
        val specs = ModelSpecs(list = listOf(spec("openAI")), addedEndpoints = listOf("agents"))
        // `agents` itself is reachable through addedEndpoints; the expansion adds openAI only.
        assertEquals(listOf("agents", "openAI"), userKeyEndpoints(configs, specs, hasAgentAccess = true))
    }

    @Test
    fun agent_providers_not_expanded_without_agent_access() {
        val specs = ModelSpecs(list = listOf(spec("openAI")), addedEndpoints = listOf("agents"))
        assertEquals(listOf("openAI"), userKeyEndpoints(endpointsConfig, specs, hasAgentAccess = false))
    }

    @Test
    fun base_url_only_endpoint_is_listed() {
        val configs = endpointsConfig + ("selfhosted" to cfg(userProvide = false, userProvideURL = true))
        assertEquals(
            listOf("anthropic", "bedrock", "openAI", "selfhosted"),
            userKeyEndpoints(configs, modelSpecs = null, hasAgentAccess = true),
        )
    }

    @Test
    fun ordered_by_order_then_label_with_missing_order_last() {
        val configs = mapOf(
            "zeta" to cfg(userProvide = true, order = null),
            "beta" to cfg(userProvide = true, order = 2),
            "alpha" to cfg(userProvide = true, order = 2, label = "Zulu"),
            "gamma" to cfg(userProvide = true, order = 1),
            "delta" to cfg(userProvide = true, order = null, label = "apple"),
        )
        assertEquals(
            listOf("gamma", "beta", "alpha", "delta", "zeta"),
            userKeyEndpoints(configs, modelSpecs = null, hasAgentAccess = true),
        )
    }
}
