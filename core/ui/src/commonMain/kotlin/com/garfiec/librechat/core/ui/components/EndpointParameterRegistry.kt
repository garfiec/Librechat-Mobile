package com.garfiec.librechat.core.ui.components

import com.garfiec.librechat.core.model.ParameterDefinition
import com.garfiec.librechat.core.model.ParameterType

/**
 * Registry of parameter definitions per endpoint, matching the official LibreChat web app's
 * parameterSettings.ts. Each endpoint maps to an ordered list of ParameterDefinition that
 * drives the dynamic ModelParameterContent rendering.
 */
@Suppress("TooManyFunctions") // Registry of per-endpoint/provider/variant param builders mirroring upstream parameterSettings.ts.
object EndpointParameterRegistry {

    /**
     * @param extendedEffortSupported when false, the `xhigh` and `max` values are filtered
     *   out of the reasoning-effort and effort dropdowns. Those values were added in
     *   upstream v0.8.5; on older servers they are rejected at request time. Per
     *   `VERSION_GATES.md`, default to the older-server behavior when the server version
     *   is unknown.
     * @param provider required for the `agents` endpoint to route to the underlying
     *   provider's parameter set. Ignored for other endpoints.
     * @param model required for the `bedrock` endpoint to dispatch on the model-prefix
     *   provider variant (anthropic.*, mistral.*, cohere.*, etc.). Cross-region inference
     *   IDs like `us.anthropic.claude-…` are also handled. Also used by the `agents`
     *   endpoint when its underlying provider is `bedrock` (for the same sub-variant
     *   dispatch). Ignored for other endpoints.
     */
    fun getDefinitions(
        endpoint: String,
        extendedEffortSupported: Boolean = false,
        provider: String? = null,
        model: String? = null,
        dropParams: List<String> = emptyList(),
    ): List<ParameterDefinition> {
        val key = endpoint.lowercase()
        // Upstream resolves the settings key from `endpointType ?? provider`, which for the agents
        // endpoint is the agent's own provider — so an Anthropic agent is subject to the same
        // per-model rules as the Anthropic endpoint.
        val settingsKey = if (key == "agents") provider?.lowercase() ?: key else key
        val base = when (key) {
            "bedrock" -> bedrockParamsForModel(model, extendedEffortSupported)
            "agents" -> agentsParamsForProvider(provider, model, extendedEffortSupported)
            else -> ENDPOINT_PARAMS[key] ?: ENDPOINT_PARAMS["default"]!!
        }
        val dropped = resolveDropParamsUIKeys(dropParams, settingsKey)
        val filtered = if (dropped.isEmpty()) base else base.filterNot { it.key in dropped }
        val modelAware = applyModelAwareDefaults(filtered, settingsKey, model)
        if (extendedEffortSupported) return modelAware
        return modelAware.map { def ->
            val options = def.options
            if (def.key in EFFORT_KEYS && options != null && options.any { it in EXTENDED_EFFORT_VALUES }) {
                def.copy(options = options.filterNot { it in EXTENDED_EFFORT_VALUES })
            } else {
                def
            }
        }
    }

    /**
     * Narrows a definition list to what the selected model actually accepts. Mirrors upstream
     * `applyModelAwareDefaults`, which runs after the `dropParams` filter and before rendering.
     *
     * [settingsKey] is the resolved parameter-set key (the agent's provider for agents), not the
     * raw endpoint name.
     */
    private fun applyModelAwareDefaults(
        definitions: List<ParameterDefinition>,
        settingsKey: String,
        model: String?,
    ): List<ParameterDefinition> {
        if (model.isNullOrBlank()) return definitions
        val adjusted = if (settingsKey == "google") {
            val bounds = googleThinkingBudgetBounds(model)
            if (bounds == null) {
                definitions
            } else {
                definitions.map { def ->
                    if (def.key == "thinkingBudget") def.withGoogleThinkingBudget(bounds) else def
                }
            }
        } else {
            definitions
        }
        if (settingsKey != "anthropic" || supportsPromptCache(model)) return adjusted
        return adjusted.filterNot { it.key in PROMPT_CACHE_KEYS }
    }

    private val PROMPT_CACHE_KEYS = setOf("promptCache", "promptCacheTtl")

    private fun ParameterDefinition.withGoogleThinkingBudget(bounds: ThinkingBudgetBounds) = copy(
        max = bounds.max.toDouble(),
        // `min` stays -1: that is the "decide automatically" sentinel, and it is not subject to the
        // positive floor. The floor only applies once a real budget is typed, so it lives in the
        // description, which is the only part of a TEXT definition this app renders.
        description = "Max tokens for thinking (-1 = dynamic, or ${bounds.min}-${bounds.max}).",
    )

    private data class ThinkingBudgetBounds(val min: Int, val max: Int)

    /**
     * MIRRORED from upstream `getGoogleThinkingBudgetBounds` (`packages/data-provider/src/schemas.ts`).
     * The shared 32,000 bound under-limits Pro and accepts Flash values the provider rejects.
     */
    private fun googleThinkingBudgetBounds(model: String): ThinkingBudgetBounds? = when {
        !GEMINI_25.containsMatchIn(model) -> null
        GEMINI_FLASH_LITE.containsMatchIn(model) -> ThinkingBudgetBounds(min = 512, max = 24576)
        GEMINI_FLASH.containsMatchIn(model) -> ThinkingBudgetBounds(min = 0, max = 24576)
        GEMINI_PRO.containsMatchIn(model) -> ThinkingBudgetBounds(min = 128, max = 32768)
        else -> null
    }

    /**
     * MIRRORED from upstream `supportsPromptCache` (`packages/data-provider/src/bedrock.ts`).
     * Matched against the configured model id, not a token-map resolution: collapsing a new Claude
     * model to the generic `claude-` fallback would wrongly hide the cache controls.
     */
    private fun supportsPromptCache(model: String): Boolean {
        if (model.contains("claude-3-5-sonnet-latest") || model.contains("claude-3.5-sonnet-latest")) {
            return false
        }
        return PROMPT_CACHE_PATTERNS.any { it.containsMatchIn(model) }
    }

    // No `\b` anywhere: Android's Regex is ICU, and a pattern that compiles on the JVM can still
    // behave differently there.
    private val GEMINI_25 = Regex("""gemini-2\.5""", RegexOption.IGNORE_CASE)
    private val GEMINI_FLASH_LITE = Regex("""flash[-_.]?lite""", RegexOption.IGNORE_CASE)
    private val GEMINI_FLASH = Regex("flash", RegexOption.IGNORE_CASE)
    private val GEMINI_PRO = Regex("pro", RegexOption.IGNORE_CASE)

    private val PROMPT_CACHE_PATTERNS = listOf(
        Regex("""claude-3[-.]7"""),
        Regex("""claude-3[-.]5-(?:sonnet|haiku)"""),
        Regex("""claude-3-(?:sonnet|haiku|opus)?"""),
        Regex("""claude-(?:sonnet|opus|haiku)[-.]?(?:[4-9]|\d{2,})"""),
        Regex("""claude-(?:[4-9]|\d{2,})(?:[-.](?:sonnet|opus|haiku))?"""),
        // MYTHOS_CLASS_FAMILIES — new top-level Claude classes, peers of opus/sonnet/haiku.
        Regex("""claude-(?:fable|mythos)[-.]?\d"""),
    )

    /**
     * MIRRORED from upstream `resolveDropParamsUIKeys` (`packages/data-provider/src/parameterSettings.ts`).
     *
     * An admin's `dropParams` names the backend field (`maxTokens`), which is also the UI key for
     * the native providers but not for the OpenAI-compatible sets, where the control is
     * `max_tokens`. Aliasing unconditionally would hide `top_p` on Anthropic, which spells its
     * own control `topP` and drops nothing.
     */
    private fun resolveDropParamsUIKeys(dropParams: List<String>, settingsKey: String): Set<String> {
        if (dropParams.isEmpty()) return emptySet()
        if (settingsKey !in OPENAI_LIKE_PARAM_KEYS) return dropParams.toSet()
        return dropParams.mapTo(mutableSetOf()) { DROP_PARAM_UI_KEYS[it] ?: it }
    }

    private val DROP_PARAM_UI_KEYS = mapOf(
        "maxTokens" to "max_tokens",
        "topP" to "top_p",
        "frequencyPenalty" to "frequency_penalty",
        "presencePenalty" to "presence_penalty",
    )

    private val OPENAI_LIKE_PARAM_KEYS = setOf("openai", "azureopenai", "custom", "openrouter")

    /**
     * Agents endpoint dispatch — mirrors upstream `agentParamSettings`
     * (derived from `presetSettings.col2` at parameterSettings.ts:1128-1135).
     * Routes to the underlying provider's parameter set so agent-mode chats
     * expose the same provider-specific knobs (thinking for Anthropic agents,
     * reasoning_effort for OpenAI agents, region for Bedrock agents, etc.).
     */
    private fun agentsParamsForProvider(
        provider: String?,
        model: String?,
        extendedEffortSupported: Boolean,
    ): List<ParameterDefinition> {
        val key = provider?.lowercase()
        val base = when (key) {
            "openai", "azureopenai", "custom" -> openAiParams()
            "anthropic" -> anthropicParams()
            "google" -> googleParams()
            "bedrock" -> bedrockParamsForModel(model, extendedEffortSupported)
            null -> defaultParams()
            else -> ENDPOINT_PARAMS[key] ?: defaultParams()
        }
        // Strip chat-level conversation overrides — the agent editor already
        // surfaces Name and Instructions as top-level fields, and upstream's
        // `agentParamSettings` (derived from `presetSettings.col2`) omits these.
        return base.filterNot { it.key in AGENT_EXCLUDED_KEYS }
    }

    private val AGENT_EXCLUDED_KEYS = setOf(
        "chatGptLabel", // OpenAI "Custom Name" — duplicates Agent.name
        "modelLabel", // Anthropic/Google/Bedrock equivalent
        "promptPrefix", // "Custom Instructions" — duplicates Agent.instructions
        // NOTE: `system` is intentionally NOT excluded. Bedrock-Anthropic and
        // Bedrock-Moonshot use `bedrock.system` (a distinct wire field, not
        // an alias of promptPrefix) and upstream `agentParamSettings`
        // (`bedrockAnthropicCol2` / `bedrockMoonshotCol2`) surfaces it in the
        // agent editor.
    )

    private val EFFORT_KEYS = setOf("reasoning_effort", "effort")
    private val EXTENDED_EFFORT_VALUES = setOf("xhigh", "max")

    // Defaults mirrored from upstream `parameterSettings.ts` / `schemas.ts`. Centralized
    // here so the registry doesn't sprinkle magic numbers across each provider's params.
    private object AnthropicDefaults {
        const val TOP_P = "0.7" // upstream parameterSettings.ts (anthropic.topP)
        const val THINKING_BUDGET = "2000" // upstream schemas.ts anthropicSettings.thinkingBudget.default
    }

    private object BedrockAnthropicDefaults {
        const val TOP_P = "0.999" // upstream parameterSettings.ts:538 (bedrockAnthropic.topP)
        const val THINKING_BUDGET = "2000" // upstream parameterSettings.ts thinkingBudget.default
    }

    private val ENDPOINT_PARAMS: Map<String, List<ParameterDefinition>> = mapOf(
        "openai" to openAiParams(),
        "azureopenai" to openAiParams(),
        "custom" to openAiParams(),
        "anthropic" to anthropicParams(),
        "google" to googleParams(),
        "default" to defaultParams(),
    )

    // ---------------------------------------------------------------------------
    // Bedrock — client-side per-provider variant dispatch.
    // Mirrors upstream `parameterSettings.ts:859-1066`: the Bedrock endpoint's
    // settings depend on the underlying provider, derived from the model id.
    // Examples:
    //   anthropic.claude-3-7-sonnet-…     → bedrockAnthropic
    //   us.anthropic.claude-3-7-sonnet-…  → bedrockAnthropic (cross-region inference)
    //   mistral.mistral-large-…           → bedrockMistral
    //   amazon.nova-lite-…                → bedrockAmazon (general)
    // ---------------------------------------------------------------------------

    private val BEDROCK_REGION_PREFIXES = listOf("us.", "eu.", "apac.", "ap.", "sa.", "ca.")

    private fun stripBedrockRegionPrefix(model: String): String {
        for (prefix in BEDROCK_REGION_PREFIXES) {
            if (model.startsWith(prefix)) return model.removePrefix(prefix)
        }
        return model
    }

    private fun bedrockParamsForModel(
        model: String?,
        extendedEffortSupported: Boolean,
    ): List<ParameterDefinition> {
        val core = model?.let(::stripBedrockRegionPrefix)
            ?: return bedrockAnthropic(extendedEffortSupported)
        return when {
            core.startsWith("anthropic.") -> bedrockAnthropic(extendedEffortSupported)
            core.startsWith("mistral.") -> bedrockMistral()
            core.startsWith("cohere.") -> bedrockCohere()
            core.startsWith("meta.") -> bedrockMeta()
            core.startsWith("ai21.") -> bedrockAi21()
            core.startsWith("amazon.") -> bedrockAmazon()
            core.startsWith("deepseek.") -> bedrockDeepseek()
            core.startsWith("moonshot.") || core.startsWith("moonshotai.") -> bedrockMoonshot()
            core.contains("zai") -> bedrockZai()
            else -> bedrockAnthropic(extendedEffortSupported)
        }
    }

    // Shared Bedrock building blocks, factored from upstream parameterSettings.ts.

    private fun bedrockModelLabel() = ParameterDefinition(
        key = "modelLabel",
        label = "Custom Name",
        type = ParameterType.TEXT,
        default = "",
        description = "Set a custom name for the AI.",
    )

    // Bedrock-Anthropic / Bedrock-Moonshot use the distinct `bedrock.system` wire
    // key (upstream `parameterSettings.ts:493-503`). Other Bedrock variants (Mistral,
    // Cohere, Meta, AI21, Amazon, DeepSeek, ZAI) use `librechat.promptPrefix`.
    // Keep the UX slot identical (textarea wired to ModelParameters.customInstructions
    // via the system|promptPrefix alias in withUpdatedKey/getValueForKey) — the
    // difference is purely the wire payload.
    private fun bedrockAnthropicSystem() = ParameterDefinition(
        key = "system",
        label = "Custom Instructions",
        type = ParameterType.TEXTAREA,
        default = "",
        description = "Set custom instructions to include in System Message.",
    )

    private fun bedrockPromptPrefix() = ParameterDefinition(
        key = "promptPrefix",
        label = "Custom Instructions",
        type = ParameterType.TEXTAREA,
        default = "",
        description = "Set custom instructions to include in System Message.",
    )

    private fun bedrockMaxContextTokens() = ParameterDefinition(
        key = "maxContextTokens",
        label = "Max Context Tokens",
        type = ParameterType.TEXT,
        default = "",
        description = "Max context tokens for this conversation.",
    )

    private fun bedrockMaxOutputTokens(defaultValue: String = "") = ParameterDefinition(
        key = "maxTokens",
        label = "Max Output Tokens",
        type = ParameterType.TEXT,
        default = defaultValue,
        description = "Max tokens in the response.",
    )

    private fun bedrockTemperature(default: String) = ParameterDefinition(
        key = "temperature",
        label = "Temperature",
        type = ParameterType.SLIDER,
        min = 0.0,
        max = 1.0,
        step = 0.01,
        default = default,
        description = "Controls randomness. Lower values are more focused and deterministic.",
    )

    private fun bedrockTopP(
        default: String,
        min: Double = 0.0,
        max: Double = 1.0,
    ) = ParameterDefinition(
        key = "topP",
        label = "Top P",
        type = ParameterType.SLIDER,
        min = min,
        max = max,
        step = 0.01,
        default = default,
        description = "Nucleus sampling. Controls diversity via cumulative probability cutoff.",
    )

    private fun bedrockTopK() = ParameterDefinition(
        key = "topK",
        label = "Top K",
        type = ParameterType.SLIDER,
        min = 0.0,
        max = 500.0,
        step = 1.0,
        default = "0",
        description = "Limits token selection to the top K most likely tokens.",
    )

    private fun bedrockStop() = ParameterDefinition(
        key = "stop",
        label = "Stop Sequences",
        type = ParameterType.TAGS,
        max = 4.0,
        default = "",
        description = "Up to 4 sequences where the model will stop generating.",
    )

    private fun bedrockRegion() = ParameterDefinition(
        key = "region",
        label = "Region",
        type = ParameterType.TEXT,
        default = "",
        description = "AWS region used for this conversation.",
    )

    private fun bedrockResendFiles() = ParameterDefinition(
        key = "resendFiles",
        label = "Resend Files",
        type = ParameterType.SWITCH,
        default = "false",
        description = "Resend previously attached files with every message.",
    )

    private fun bedrockPromptCache(default: String) = ParameterDefinition(
        key = "promptCache",
        label = "Prompt Caching",
        type = ParameterType.SWITCH,
        default = default,
        description = "Enable prompt caching to reduce latency and cost.",
    )

    private fun bedrockFileTokenLimit() = ParameterDefinition(
        key = "fileTokenLimit",
        label = "File Token Limit",
        type = ParameterType.TEXT,
        default = "",
        description = "Maximum number of tokens from attached files.",
    )

    private fun bedrockImageDetail() = ParameterDefinition(
        key = "imageDetail",
        label = "Image Detail",
        type = ParameterType.ENUM_SLIDER,
        options = listOf("low", "auto", "high"),
        default = "auto",
        description = "Detail level for image inputs.",
    )

    /**
     * Bedrock — Anthropic variant. Mirrors upstream `bedrockAnthropic`
     * (parameterSettings.ts:859-876).
     *
     * Note: the wire key is `reasoning_effort`, not `effort`. The Bedrock-Anthropic
     * effort dropdown intentionally omits `xhigh` and `max` — only the values
     * [unset, low, medium, high] are valid per upstream `bedrock.reasoning_effort`.
     */
    @Suppress("UnusedParameter")
    private fun bedrockAnthropic(extendedEffortSupported: Boolean) = listOf(
        bedrockModelLabel(),
        bedrockAnthropicSystem(),
        bedrockMaxContextTokens(),
        bedrockMaxOutputTokens(),
        bedrockTemperature(default = "1.0"),
        bedrockTopP(default = BedrockAnthropicDefaults.TOP_P),
        bedrockTopK(),
        bedrockStop(),
        bedrockResendFiles(),
        bedrockRegion(),
        bedrockPromptCache(default = "true"),
        ParameterDefinition(
            key = "thinking",
            label = "Thinking",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Enable extended thinking.",
        ),
        ParameterDefinition(
            key = "thinkingBudget",
            label = "Thinking Budget",
            type = ParameterType.TEXT,
            min = 1024.0,
            max = 200000.0,
            default = BedrockAnthropicDefaults.THINKING_BUDGET,
            description = "Max tokens for thinking (1024-200000).",
        ),
        ParameterDefinition(
            key = "reasoning_effort",
            label = "Reasoning Effort",
            type = ParameterType.ENUM_SLIDER,
            // Bedrock-Anthropic effort: enum is [unset, low, medium, high] only;
            // xhigh/max are NOT valid here — see upstream bedrock.reasoning_effort.
            options = listOf("", "low", "medium", "high"),
            default = "",
            description = "Controls the overall effort level of the response.",
        ),
        ParameterDefinition(
            key = "thinkingDisplay",
            label = "Reasoning Visibility",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("auto", "summarized", "omitted"),
            default = "auto",
            description = "Controls whether reasoning tokens are streamed to the client (Claude Opus 4.7+).",
        ),
        bedrockImageDetail(),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    // Bedrock — Mistral variant. Mirrors upstream `bedrockMistral`.
    private fun bedrockMistral() = listOf(
        bedrockModelLabel(),
        bedrockPromptPrefix(),
        bedrockMaxContextTokens(),
        bedrockMaxOutputTokens(),
        bedrockTemperature(default = "0.7"),
        bedrockTopP(default = "1.0"),
        bedrockResendFiles(),
        bedrockRegion(),
        bedrockStop(),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    // Bedrock — Cohere variant. Mirrors upstream `bedrockCohere`.
    private fun bedrockCohere() = listOf(
        bedrockModelLabel(),
        bedrockPromptPrefix(),
        bedrockMaxContextTokens(),
        bedrockMaxOutputTokens(),
        bedrockTemperature(default = "0.3"),
        bedrockTopP(default = "0.75", min = 0.01, max = 0.99),
        bedrockResendFiles(),
        bedrockRegion(),
        bedrockStop(),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    // Bedrock — Meta / general (also used for AI21, Amazon, DeepSeek upstream).
    private fun bedrockGeneral() = listOf(
        bedrockModelLabel(),
        bedrockPromptPrefix(),
        bedrockMaxContextTokens(),
        bedrockTemperature(default = "0.5"),
        bedrockTopP(default = "0.9"),
        bedrockResendFiles(),
        bedrockRegion(),
        bedrockPromptCache(default = "false"),
        bedrockStop(),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    private fun bedrockMeta() = bedrockGeneral()
    private fun bedrockAi21() = bedrockGeneral()
    private fun bedrockAmazon() = bedrockGeneral()
    private fun bedrockDeepseek() = bedrockGeneral()

    // Bedrock — Moonshot variant. Mirrors upstream `bedrockMoonshot`.
    private fun bedrockMoonshot() = listOf(
        bedrockModelLabel(),
        bedrockAnthropicSystem(),
        bedrockMaxContextTokens(),
        bedrockMaxOutputTokens(defaultValue = "16384"),
        bedrockTemperature(default = "1.0"),
        bedrockTopP(default = BedrockAnthropicDefaults.TOP_P),
        bedrockStop(),
        bedrockResendFiles(),
        bedrockRegion(),
        ParameterDefinition(
            key = "reasoning_effort",
            label = "Reasoning Effort",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("", "low", "medium", "high"),
            default = "",
            description = "Controls the overall effort level of the response.",
        ),
        bedrockImageDetail(),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    // Bedrock — ZAI variant. Mirrors upstream `bedrockZAI`.
    private fun bedrockZai() = listOf(
        bedrockModelLabel(),
        bedrockPromptPrefix(),
        bedrockMaxContextTokens(),
        bedrockTemperature(default = "0.5"),
        bedrockTopP(default = "0.9"),
        bedrockResendFiles(),
        bedrockRegion(),
        ParameterDefinition(
            key = "reasoning_effort",
            label = "Reasoning Effort",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("", "low", "medium", "high"),
            default = "",
            description = "Controls the overall effort level of the response.",
        ),
        bedrockFileTokenLimit(),
        tagsDefinition(),
    )

    private fun openAiParams() = listOf(
        ParameterDefinition(
            key = "chatGptLabel",
            label = "Custom Name",
            type = ParameterType.TEXT,
            default = "",
            placeholder = "ChatGPT",
            description = "Set a custom name for the AI.",
        ),
        ParameterDefinition(
            key = "promptPrefix",
            label = "Custom Instructions",
            type = ParameterType.TEXTAREA,
            default = "",
            placeholder = "Add a custom instruction to include in every message...",
            description = "Set custom instructions to include in System Message.",
        ),
        ParameterDefinition(
            key = "maxContextTokens",
            label = "Max Context Tokens",
            type = ParameterType.TEXT,
            default = "",
            description = "Max context tokens for this conversation.",
        ),
        ParameterDefinition(
            key = "max_tokens",
            label = "Max Output Tokens",
            type = ParameterType.TEXT,
            default = "",
            description = "Max tokens in the response.",
        ),
        ParameterDefinition(
            key = "temperature",
            label = "Temperature",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 2.0,
            step = 0.01,
            default = "1.0",
            description = "Controls randomness. Lower values are more focused and deterministic.",
        ),
        ParameterDefinition(
            key = "top_p",
            label = "Top P",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            default = "1.0",
            description = "Nucleus sampling. Controls diversity via cumulative probability cutoff.",
        ),
        ParameterDefinition(
            key = "frequency_penalty",
            label = "Frequency Penalty",
            type = ParameterType.SLIDER,
            min = -2.0,
            max = 2.0,
            step = 0.01,
            default = "0.0",
            description = "Penalizes tokens based on their frequency in the text so far.",
        ),
        ParameterDefinition(
            key = "presence_penalty",
            label = "Presence Penalty",
            type = ParameterType.SLIDER,
            min = -2.0,
            max = 2.0,
            step = 0.01,
            default = "0.0",
            description = "Penalizes tokens based on whether they appear in the text so far.",
        ),
        ParameterDefinition(
            key = "stop",
            label = "Stop Sequences",
            type = ParameterType.TAGS,
            max = 4.0,
            default = "",
            description = "Up to 4 sequences where the model will stop generating.",
        ),
        ParameterDefinition(
            key = "resendFiles",
            label = "Resend Files",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Resend previously attached files with every message.",
        ),
        ParameterDefinition(
            key = "reasoning_effort",
            label = "Reasoning Effort",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("", "none", "minimal", "low", "medium", "high", "xhigh"),
            default = "",
            description = "Controls how much reasoning the model performs.",
        ),
        ParameterDefinition(
            key = "reasoning_summary",
            label = "Reasoning Summary",
            type = ParameterType.ENUM_SLIDER,
            // Upstream's ReasoningSummary.none wire value is `""`; relabel as Unset.
            options = listOf("", "auto", "concise", "detailed"),
            default = "",
            optionLabels = mapOf("" to "Unset"),
            description = "How reasoning summary is exposed in the response.",
        ),
        ParameterDefinition(
            key = "verbosity",
            label = "Verbosity",
            type = ParameterType.ENUM_SLIDER,
            // Upstream's Verbosity.none wire value is `""`; relabel as Unset.
            options = listOf("", "low", "medium", "high"),
            default = "",
            optionLabels = mapOf("" to "Unset"),
            description = "Controls how concise or verbose responses are.",
        ),
        ParameterDefinition(
            key = "useResponsesApi",
            label = "Use Responses API",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Route requests through OpenAI's Responses API.",
        ),
        ParameterDefinition(
            key = "disableStreaming",
            label = "Disable Streaming",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Wait for the full response instead of streaming tokens.",
        ),
        ParameterDefinition(
            key = "imageDetail",
            label = "Image Detail",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("low", "auto", "high"),
            default = "auto",
            description = "Detail level for image inputs.",
        ),
        ParameterDefinition(
            key = "web_search",
            label = "Web Search",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Enable web search to get up-to-date information.",
        ),
        ParameterDefinition(
            key = "fileTokenLimit",
            label = "File Token Limit",
            type = ParameterType.TEXT,
            default = "",
            description = "Maximum number of tokens from attached files.",
        ),
        tagsDefinition(),
    )

    private fun anthropicParams() = listOf(
        ParameterDefinition(
            key = "modelLabel",
            label = "Custom Name",
            type = ParameterType.TEXT,
            default = "",
            placeholder = "Claude",
            description = "Set a custom name for the AI.",
        ),
        ParameterDefinition(
            key = "promptPrefix",
            label = "Custom Instructions",
            type = ParameterType.TEXTAREA,
            default = "",
            placeholder = "Add a custom instruction to include in every message...",
            description = "Set custom instructions to include in System Message.",
        ),
        ParameterDefinition(
            key = "maxContextTokens",
            label = "Max Context Tokens",
            type = ParameterType.TEXT,
            default = "",
            description = "Max context tokens for this conversation.",
        ),
        ParameterDefinition(
            key = "maxOutputTokens",
            label = "Max Output Tokens",
            type = ParameterType.TEXT,
            min = 1.0,
            max = 128000.0,
            default = "",
            description = "Max tokens in the response (1-128000).",
        ),
        ParameterDefinition(
            key = "temperature",
            label = "Temperature",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            default = "1.0",
            description = "Controls randomness. Lower values are more focused and deterministic.",
        ),
        ParameterDefinition(
            key = "topP",
            label = "Top P",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            default = AnthropicDefaults.TOP_P,
            description = "Nucleus sampling. Controls diversity via cumulative probability cutoff.",
        ),
        ParameterDefinition(
            key = "topK",
            label = "Top K",
            type = ParameterType.SLIDER,
            min = 1.0,
            max = 40.0,
            step = 1.0,
            default = "5",
            description = "Limits token selection to the top K most likely tokens.",
        ),
        ParameterDefinition(
            key = "resendFiles",
            label = "Resend Files",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Resend previously attached files with every message.",
        ),
        ParameterDefinition(
            key = "promptCache",
            label = "Prompt Caching",
            type = ParameterType.SWITCH,
            default = "true",
            description = "Enable prompt caching to reduce latency and cost.",
        ),
        ParameterDefinition(
            key = "promptCacheTtl",
            label = "Prompt Cache Duration",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("5m", "1h"),
            default = "5m",
            description = "How long prompt caches persist (Anthropic). Applies when prompt caching is on.",
        ),
        ParameterDefinition(
            key = "thinking",
            label = "Thinking",
            type = ParameterType.SWITCH,
            default = "true",
            description = "Enable extended thinking.",
        ),
        ParameterDefinition(
            key = "thinkingBudget",
            label = "Thinking Budget",
            type = ParameterType.TEXT,
            min = 1024.0,
            max = 200000.0,
            default = AnthropicDefaults.THINKING_BUDGET,
            description = "Max tokens for thinking (1024-200000).",
        ),
        ParameterDefinition(
            key = "effort",
            label = "Effort",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("", "low", "medium", "high", "xhigh", "max"),
            default = "",
            description = "Controls the overall effort level of the response.",
        ),
        ParameterDefinition(
            key = "thinkingDisplay",
            label = "Reasoning Visibility",
            type = ParameterType.ENUM_SLIDER,
            options = listOf("auto", "summarized", "omitted"),
            default = "auto",
            description = "Controls whether reasoning tokens are streamed to the client (Claude Opus 4.7+).",
        ),
        ParameterDefinition(
            key = "web_search",
            label = "Web Search",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Enable web search to get up-to-date information.",
        ),
        ParameterDefinition(
            key = "fileTokenLimit",
            label = "File Token Limit",
            type = ParameterType.TEXT,
            default = "",
            description = "Maximum number of tokens from attached files.",
        ),
        tagsDefinition(),
    )

    private fun googleParams() = listOf(
        ParameterDefinition(
            key = "modelLabel",
            label = "Custom Name",
            type = ParameterType.TEXT,
            default = "",
            placeholder = "Gemini",
            description = "Set a custom name for the AI.",
        ),
        ParameterDefinition(
            key = "promptPrefix",
            label = "Custom Instructions",
            type = ParameterType.TEXTAREA,
            default = "",
            placeholder = "Add a custom instruction to include in every message...",
            description = "Set custom instructions to include in System Message.",
        ),
        ParameterDefinition(
            key = "maxContextTokens",
            // Upstream scopes this definition to the Google endpoint (googleSettings.maxContextTokens)
            // rather than sharing the unbounded one, whose other endpoints have smaller windows.
            label = "Max Context Tokens",
            type = ParameterType.TEXT,
            min = 10.0,
            max = 2000000.0,
            step = 1000.0,
            default = "",
            description = "Max context tokens for this conversation.",
        ),
        ParameterDefinition(
            key = "maxOutputTokens",
            label = "Max Output Tokens",
            type = ParameterType.TEXT,
            min = 1.0,
            max = 64000.0,
            default = "",
            description = "Max tokens in the response (1-64000).",
        ),
        ParameterDefinition(
            key = "temperature",
            label = "Temperature",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 2.0,
            step = 0.01,
            default = "1.0",
            description = "Controls randomness. Lower values are more focused and deterministic.",
        ),
        ParameterDefinition(
            key = "topP",
            label = "Top P",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            default = "0.95",
            description = "Nucleus sampling. Controls diversity via cumulative probability cutoff.",
        ),
        ParameterDefinition(
            key = "topK",
            label = "Top K",
            type = ParameterType.SLIDER,
            min = 1.0,
            max = 40.0,
            step = 1.0,
            default = "40",
            description = "Limits token selection to the top K most likely tokens.",
        ),
        ParameterDefinition(
            key = "resendFiles",
            label = "Resend Files",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Resend previously attached files with every message.",
        ),
        ParameterDefinition(
            key = "thinking",
            label = "Thinking",
            type = ParameterType.SWITCH,
            default = "true",
            description = "Enable extended thinking.",
        ),
        ParameterDefinition(
            key = "thinkingBudget",
            label = "Thinking Budget",
            type = ParameterType.TEXT,
            min = -1.0,
            max = 32000.0,
            default = "",
            description = "Max tokens for thinking (-1 = dynamic, up to 32000).",
        ),
        ParameterDefinition(
            key = "thinkingLevel",
            label = "Thinking Level",
            type = ParameterType.ENUM_SLIDER,
            // Mirror upstream ThinkingLevel enum (schemas.ts): ['', minimal, low,
            // medium, high]. "none" is NOT a valid value — the server validates
            // via z.nativeEnum and drops it; "minimal" is the lowest level.
            options = listOf("", "minimal", "low", "medium", "high"),
            default = "",
            description = "Controls how much thinking the model performs.",
        ),
        ParameterDefinition(
            key = "web_search",
            label = "Grounding with Google Search",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Enable web search to get up-to-date information.",
        ),
        ParameterDefinition(
            key = "url_context",
            label = "Use URL Context",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Let the model fetch and ground on URLs (and YouTube) you reference.",
        ),
        ParameterDefinition(
            key = "fileTokenLimit",
            label = "File Token Limit",
            type = ParameterType.TEXT,
            default = "",
            description = "Maximum number of tokens from attached files.",
        ),
        tagsDefinition(),
    )

    private fun defaultParams() = listOf(
        ParameterDefinition(
            key = "modelLabel",
            label = "Custom Name",
            type = ParameterType.TEXT,
            default = "",
            placeholder = "Assistant",
            description = "Set a custom name for the AI.",
        ),
        ParameterDefinition(
            key = "promptPrefix",
            label = "Custom Instructions",
            type = ParameterType.TEXTAREA,
            default = "",
            placeholder = "Add a custom instruction to include in every message...",
            description = "Set custom instructions to include in System Message.",
        ),
        ParameterDefinition(
            key = "maxContextTokens",
            label = "Max Context Tokens",
            type = ParameterType.TEXT,
            default = "",
            description = "Max context tokens for this conversation.",
        ),
        ParameterDefinition(
            key = "maxOutputTokens",
            label = "Max Output Tokens",
            type = ParameterType.TEXT,
            default = "",
            description = "Max tokens in the response.",
        ),
        ParameterDefinition(
            key = "temperature",
            label = "Temperature",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 2.0,
            step = 0.01,
            default = "1.0",
            description = "Controls randomness. Lower values are more focused and deterministic.",
        ),
        ParameterDefinition(
            key = "topP",
            label = "Top P",
            type = ParameterType.SLIDER,
            min = 0.0,
            max = 1.0,
            step = 0.01,
            default = "1.0",
            description = "Nucleus sampling. Controls diversity via cumulative probability cutoff.",
        ),
        ParameterDefinition(
            key = "resendFiles",
            label = "Resend Files",
            type = ParameterType.SWITCH,
            default = "false",
            description = "Resend previously attached files with every message.",
        ),
        ParameterDefinition(
            key = "fileTokenLimit",
            label = "File Token Limit",
            type = ParameterType.TEXT,
            default = "",
            description = "Maximum number of tokens from attached files.",
        ),
        tagsDefinition(),
    )

    /** Conversation/preset tag list. Round-trips through ModelParameters.dynamicValues["tags"]
     *  as a newline-joined string; PresetPromptDelegate splits it into Preset.tags on save. */
    private fun tagsDefinition() = ParameterDefinition(
        key = "tags",
        label = "Tags",
        type = ParameterType.TAGS,
        default = "",
        description = "Tags for organizing presets and conversations.",
    )
}
