package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * MIRRORED from upstream `packages/api/src/agents/inference.ts` (v0.8.9-rc1, #17220): the inference
 * parameters an agent's `model_parameters` may hold, per provider family. From v0.8.9-rc1 the
 * agent create/update routes reject any other key with a 400, so a save that round-trips a key the
 * server stored before (or one another family takes) would fail every time. Registered in
 * scripts/mirrors.json as `agent-inference-profiles`.
 */

private val COMMON = setOf(
    "maxContextTokens", "fileTokenLimit", "resendFiles", "imageDetail", "promptPrefix", "disableStreaming",
)
private val OPENAI = COMMON + setOf(
    "temperature", "top_p", "frequency_penalty", "presence_penalty", "max_tokens", "stop",
    "reasoning_effort", "reasoning_summary", "reasoning_mode", "reasoning_context", "verbosity",
    "useResponsesApi", "web_search",
)
private val OPENROUTER = OPENAI + setOf("promptCache", "promptCacheTtl")
private val ANTHROPIC = COMMON + setOf(
    "temperature", "topP", "topK", "maxOutputTokens", "stop", "promptCache", "promptCacheTtl",
    "thinking", "thinkingBudget", "effort", "thinkingDisplay", "web_search",
)
private val GOOGLE = COMMON + setOf(
    "temperature", "topP", "topK", "maxOutputTokens", "stop", "thinking", "thinkingBudget",
    "thinkingLevel", "web_search", "url_context",
)
private val BEDROCK = COMMON + setOf(
    "region", "reasoning_effort", "temperature", "topP", "topK", "maxTokens", "stop", "system",
    "promptCache", "promptCacheTtl",
)
private val BEDROCK_ANTHROPIC = BEDROCK + setOf("thinking", "thinkingBudget", "effort", "thinkingDisplay")
private val MISTRAL = COMMON + setOf("temperature", "topP", "maxTokens", "stop")

private val BUILTIN_KEYS = OPENAI + ANTHROPIC + GOOGLE + BEDROCK + MISTRAL

private val ALIASES = mapOf(
    "max_context_tokens" to "maxContextTokens",
    "frequencyPenalty" to "frequency_penalty",
    "presencePenalty" to "presence_penalty",
    "thinking_budget" to "thinkingBudget",
    "top_k" to "topK",
    "stopSequences" to "stop",
    "stop_sequences" to "stop",
    "reasoningEffort" to "reasoning_effort",
    "reasoningSummary" to "reasoning_summary",
    "reasoningMode" to "reasoning_mode",
    "reasoningContext" to "reasoning_context",
)

private enum class Family { OPENAI, ANTHROPIC, GOOGLE, BEDROCK, MISTRAL }

private fun knownFamily(provider: String?): Family? = when (provider?.lowercase()) {
    "openai", "azureopenai" -> Family.OPENAI
    "anthropic" -> Family.ANTHROPIC
    "google", "vertexai" -> Family.GOOGLE
    "bedrock" -> Family.BEDROCK
    "mistral", "mistralai" -> Family.MISTRAL
    else -> null
}

private val MAX_TOKEN_KEYS = setOf("max_tokens", "maxTokens", "maxOutputTokens", "max_output_tokens")
private val CLAUDE_IN_MODEL = Regex("anthropic|claude", RegexOption.IGNORE_CASE)

/** Upstream `parameterKey`: the profile key [key] is validated under for [family]. */
private fun parameterKey(key: String, family: Family): String = when {
    key == "topP" || key == "top_p" -> if (family == Family.OPENAI) "top_p" else "topP"
    key in MAX_TOKEN_KEYS -> when (family) {
        Family.OPENAI -> "max_tokens"
        Family.BEDROCK, Family.MISTRAL -> "maxTokens"
        else -> "maxOutputTokens"
    }
    else -> ALIASES[key] ?: key
}

/**
 * What an endpoint's `customParams` says about agent inference (served on `/api/endpoints`): the
 * family its parameters follow and the extra keys an administrator declared.
 */
data class AgentInferenceEndpoint(
    val defaultParamsEndpoint: String? = null,
    val declaredKeys: Set<String> = emptySet(),
)

/** Whether [provider] is a built-in family, whose accepted keys need no endpoint config. */
internal fun isBuiltInInferenceProvider(provider: String?): Boolean = knownFamily(provider) != null

private val CUSTOM_KEY = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

/**
 * Keeps the [parameters] the server accepts for an agent on [provider]/[model] (upstream
 * `resolveAgentInferenceParameters`), dropping the rest. Values are not checked: a value out of
 * range is the user's to fix, and the server's 400 names it. Two legacy shapes are carried over the
 * way the server reads them: a Bedrock agent's `additionalModelRequestFields` thinking and effort
 * preferences move to `thinking` / `effort` / `reasoning_effort` (the envelope itself is refused),
 * and `model` is kept only when it is the agent's own.
 */
internal fun keepAcceptedInferenceParameters(
    parameters: Map<String, JsonElement>,
    provider: String?,
    model: String?,
    endpoint: AgentInferenceEndpoint?,
): Map<String, JsonElement> {
    val native = knownFamily(provider) ?: Family.MISTRAL.takeIf { provider?.lowercase()?.startsWith("mistral") == true }
    val family = (if (native == null || native == Family.OPENAI) knownFamily(endpoint?.defaultParamsEndpoint) else null)
        ?: native ?: Family.OPENAI
    val isOpenRouter = provider.equals("openrouter", ignoreCase = true) ||
        endpoint?.defaultParamsEndpoint.equals("openrouter", ignoreCase = true)
    val fields = when {
        family == Family.BEDROCK && model != null && CLAUDE_IN_MODEL.containsMatchIn(model) -> BEDROCK_ANTHROPIC
        family == Family.OPENAI && isOpenRouter -> OPENROUTER
        family == Family.OPENAI -> OPENAI
        family == Family.ANTHROPIC -> ANTHROPIC
        family == Family.GOOGLE -> GOOGLE
        family == Family.BEDROCK -> BEDROCK
        else -> MISTRAL
    }
    val declared = endpoint?.declaredKeys.orEmpty().map { parameterKey(it, family) }.toSet()
    val source = parameters.toMutableMap()
    if (family == Family.BEDROCK) hoistBedrockPreferences(source, claude = fields === BEDROCK_ANTHROPIC)
    return source.filter { (key, _) ->
        if (key == "model") return@filter model != null && (source[key] as? JsonPrimitive)?.content == model
        val target = parameterKey(key, family)
        target in fields ||
            (target !in BUILTIN_KEYS && target in declared && CUSTOM_KEY.matches(key) && !key.lowercase().startsWith("lc_"))
    }
}

/** Upstream's read of a legacy Bedrock `additionalModelRequestFields` envelope. */
private fun hoistBedrockPreferences(source: MutableMap<String, JsonElement>, claude: Boolean) {
    val stored = source["additionalModelRequestFields"] as? JsonObject ?: return
    if (claude) {
        if ("thinking" !in source) (stored["thinking"] as? JsonObject)?.let { source["thinking"] = it }
        if ("effort" !in source) {
            ((stored["output_config"] as? JsonObject)?.get("effort") as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.let { source["effort"] = it }
        }
    } else if ("reasoning_effort" !in source) {
        (stored["reasoning_config"] as? JsonPrimitive)?.takeIf { it.isString }?.let { source["reasoning_effort"] = it }
    }
}

private val rejectionJson = Json { ignoreUnknownKeys = true }

/**
 * The server's own words for a `model_parameters` value the agent routes refused (v0.8.9-rc1): the
 * 400 is `{ error: "Invalid request data", details: [{ path: ["model_parameters", key], message }] }`,
 * whose top-level `error` names no parameter. Returns each distinct message with the keys it
 * names, or null when [e] is not that refusal.
 */
internal fun inferenceParameterRejection(e: Throwable?): String? {
    val api = e as? ApiException ?: return null
    if (api.statusCode != HTTP_BAD_REQUEST) return null
    val body = api.body?.let { runCatching { rejectionJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?: return null
    val issues = (body["details"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val byMessage = linkedMapOf<String, MutableList<String>>()
    for (issue in issues) {
        val path = (issue["path"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        if (path.firstOrNull() != "model_parameters") continue
        val message = (issue["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        byMessage.getOrPut(message) { mutableListOf() }.addAll(path.drop(1))
    }
    if (byMessage.isEmpty()) return null
    return byMessage.entries.joinToString("\n") { (message, keys) ->
        if (keys.isEmpty()) message else "$message (${keys.distinct().joinToString(", ")})"
    }
}

private const val HTTP_BAD_REQUEST = 400
