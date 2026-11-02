package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class EndpointConfig(
    val type: String? = null,
    val order: Int? = null,
    val iconURL: String? = null,
    /**
     * The brand a custom endpoint draws as, resolved by the server from its `iconURL`, `baseURL` host,
     * `provider` and name (a [com.garfiec.librechat.core.model.media.ProviderId] id). Kept a string so
     * an id this client doesn't know still decodes; older servers omit it.
     */
    val providerId: String? = null,
    val modelDisplayLabel: String? = null,
    val name: String? = null,
    val userProvide: Boolean? = null,
    val userProvideURL: Boolean? = null,
    val capabilities: List<String> = emptyList(),
    val disableBuilder: Boolean? = null,
    val azure: Boolean? = null,
    /**
     * The providers an agent may run on, sent on the `agents` entry only. Empty means
     * unrestricted, which is also what a server that omits the field means.
     */
    val allowedProviders: List<String> = emptyList(),
    /**
     * Bedrock's per-credential flags: which of the AWS credentials the user supplies. The server
     * sets each from its `BEDROCK_AWS_*` env var being `user_provided`; older servers omit them.
     */
    val userProvideAccessKeyId: Boolean? = null,
    val userProvideSecretAccessKey: Boolean? = null,
    val userProvideSessionToken: Boolean? = null,
    val userProvideBearerToken: Boolean? = null,
    /**
     * A custom endpoint's parameter settings: the endpoint whose parameter set it follows
     * (`defaultParamsEndpoint`) and the controls an administrator declared (`paramDefinitions`).
     * Absent on other endpoints.
     */
    val customParams: CustomParams? = null,
)

/**
 * A custom endpoint's `customParams` as `/api/endpoints` serves them. The chat reads only
 * [defaultParamsEndpoint]; the agent editor also reads [declaredKeys] to keep `model_parameters` to
 * what the server accepts (v0.8.9-rc1).
 */
@Serializable
data class CustomParams(
    /**
     * The endpoint whose parameter set this one uses (`anthropic`, `google`, `openrouter`, …). The
     * server fills it from the endpoint's `provider` unless the admin chose one other than `custom`.
     */
    val defaultParamsEndpoint: String? = null,
    /** Only each definition's `key` is read; the rest stays raw so a new field never fails the decode. */
    val paramDefinitions: List<JsonObject>? = null,
) {
    val declaredKeys: Set<String>
        get() = paramDefinitions.orEmpty().mapNotNull { def ->
            (def["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }.toSet()
}

/** The sync's name for [CustomParams]. */
typealias EndpointCustomParams = CustomParams
