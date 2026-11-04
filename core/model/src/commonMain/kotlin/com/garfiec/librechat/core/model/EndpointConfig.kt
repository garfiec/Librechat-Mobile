package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
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
    /**
     * The agents entry's tool-approval policy (v0.8.9-rc1). The server already ANDs `agentModes`
     * with `enabled`. The glob lists are for display only (which modes the editor may offer); the
     * server enforces the real policy.
     */
    val toolApproval: EndpointToolApproval? = null,
    /**
     * The agents entry's attached code machines (`environments`, already scoped to the caller)
     * and whether a chat may pick among an agent's allowed machines.
     */
    val statefulCodeSessions: StatefulCodeSessionsConfig? = null,
)

@Serializable
data class EndpointToolApproval(
    val enabled: Boolean? = null,
    val agentModes: Boolean? = null,
    /** `default`, `dontAsk` or `bypass`. */
    val mode: String? = null,
    val allow: List<String>? = null,
    val deny: List<String>? = null,
    val ask: List<String>? = null,
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
    /** Read by key (and a reasoning key's `options`); the rest stays raw so a new field never fails the decode. */
    val paramDefinitions: List<JsonObject>? = null,
    /**
     * How the endpoint takes reasoning (v0.8.9-rc1): `disabled` means none, any other value marks it
     * as taking one. The server stamps `reasoning_effort` on an OpenRouter endpoint that declares none.
     */
    val reasoningFormat: String? = null,
) {
    val declaredKeys: Set<String>
        get() = paramDefinitions.orEmpty().mapNotNull { def ->
            (def["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }.toSet()

    /** Each declared definition's key, and its `options` and `type` when it states them, in order. */
    val declaredParams: List<DeclaredParam>
        get() = paramDefinitions.orEmpty().mapNotNull { def ->
            val key = (def["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            DeclaredParam(
                key = key,
                options = (def["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content },
                type = (def["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            )
        }

    companion object {
        /** MIRRORED from upstream `ReasoningParameterFormat.disabled` (`schemas.ts`). */
        const val REASONING_FORMAT_DISABLED = "disabled"
    }
}

/** One administrator-declared parameter definition, as far as the client reads it. */
data class DeclaredParam(
    val key: String,
    /** The values it offers, or null when it states none (the built-in list applies). */
    val options: List<String>?,
    /** `enum`, `number`, … or null when it states none. */
    val type: String?,
)
