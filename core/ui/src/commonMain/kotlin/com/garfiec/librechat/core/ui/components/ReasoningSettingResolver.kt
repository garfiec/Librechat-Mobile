package com.garfiec.librechat.core.ui.components

import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.ReasoningOverrideKeys
import com.garfiec.librechat.core.model.reasoning.ModelReasoning

/**
 * The reasoning control one target (endpoint and model) takes for a one-turn override: [key] and
 * the values it accepts. [options] is null for a numeric key ([isNumeric], `thinkingBudget`), whose
 * range the server checks. [unsetIsOff] says the empty value means "no reasoning" rather than "let
 * the provider decide" (Bedrock's Moonshot and Z.AI effort).
 */
data class ReasoningSetting(
    val key: String,
    val options: List<String>?,
    val isNumeric: Boolean = false,
    val unsetIsOff: Boolean = false,
)

/**
 * What the provider's catalog says about one model's efforts, for an endpoint whose efforts come
 * from OpenRouter's catalog (`ReasoningSettingResolver.usesCatalog`). Any other endpoint passes
 * [Generic].
 */
sealed interface CatalogEfforts {
    /** Not a catalog endpoint, or the catalog does not list the model: the generic efforts stay. */
    data object Generic : CatalogEfforts

    /** The catalog is not known (loading, failed, expired): no effort is offered. */
    data object Unknown : CatalogEfforts

    /** The catalog's entry for the model; an empty list means it takes no effort. */
    data class Listed(val reasoning: ModelReasoning) : CatalogEfforts

    companion object {
        /**
         * Looks [model] up in an endpoint's catalog ([models] null while unknown). A routing variant
         * (`model:nitro`) matches its base model. MIRRORED from upstream `getModelReasoning`.
         */
        fun of(models: Map<String, ModelReasoning>?, model: String): CatalogEfforts {
            if (models == null) return Unknown
            val listed = models[model] ?: models[model.substringBefore(':')] ?: return Generic
            return Listed(listed)
        }
    }
}

/**
 * Port of upstream `resolveReasoningSettingForTarget` and the helpers it calls
 * (`packages/data-provider/src/parameterSettings.ts`), which the server runs too: an override the
 * client resolves differently is one the server refuses with a 400. Only the reasoning keys of each
 * parameter set are mirrored ([SETTINGS]), not the full lists [EndpointParameterRegistry] renders,
 * because those differ from upstream's in places (Bedrock Anthropic's effort key).
 */
object ReasoningSettingResolver {

    /**
     * Whether [customParams]' efforts come from OpenRouter's per-model catalog, so the client must
     * fetch it before offering any. MIRRORED from upstream `usesReasoningCapabilities`.
     */
    fun usesCatalog(customParams: CustomParams?): Boolean =
        customParams?.defaultParamsEndpoint == OPENROUTER &&
            customParams.reasoningFormat != CustomParams.REASONING_FORMAT_DISABLED &&
            ReasoningOverrideKeys.REASONING_EFFORT !in customParams.declaredKeys

    /**
     * The reasoning control for [model] on [endpoint], or null when none resolves.
     *
     * @param endpoint the endpoint's type when it has one (`custom`), else its name; for an agent,
     *   the agent's provider resolved the same way.
     * @param blockedKeys keys a model spec under `enforce` sets: an override of one is refused.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun resolve(
        endpoint: String,
        model: String?,
        customParams: CustomParams? = null,
        blockedKeys: Set<String> = emptySet(),
        catalog: CatalogEfforts = CatalogEfforts.Generic,
    ): ReasoningSetting? {
        if (model.isNullOrEmpty()) return null
        if (customParams?.reasoningFormat == CustomParams.REASONING_FORMAT_DISABLED) return null
        val combinedKey = "$endpoint-${modelKey(endpoint, model)}"
        val defaultParamsEndpoint = customParams?.defaultParamsEndpoint ?: endpoint
        val declared = declaredReasoningSettings(customParams)

        val hasExplicitCapability = declared.isNotEmpty() ||
            customParams?.reasoningFormat != null ||
            defaultParamsEndpoint in KNOWN_REASONING_PROVIDERS
        val usesGenericSettings = endpoint == CUSTOM || endpoint == OPENROUTER || SETTINGS[endpoint] == null
        if (usesGenericSettings && !hasExplicitCapability) return null

        val base = SETTINGS[combinedKey] ?: SETTINGS[defaultParamsEndpoint] ?: SETTINGS[endpoint] ?: emptyList()
        // An administrator-defined reasoning_effort is authoritative: the catalog never narrows it.
        val effectiveCatalog =
            if (declared.any { it.key == ReasoningOverrideKeys.REASONING_EFFORT }) CatalogEfforts.Generic else catalog
        val modelAware = applyModelAwareDefaults(base, defaultParamsEndpoint, model, effectiveCatalog)
        val overlaid = modelAware.map { setting ->
            declared.firstOrNull { it.key == setting.key }?.over(setting) ?: setting
        }
        val settings = overlaid + declared.filter { d -> overlaid.none { it.key == d.key } }.map { it.over(null) }

        val explicitKey = declared.firstOrNull()?.key
        if (explicitKey != null) {
            val explicit = settings.firstOrNull { it.key == explicitKey }
            return if (explicit != null && explicit.key in blockedKeys) null else submittable(explicit)
        }
        val resolutionEndpoint =
            if (defaultParamsEndpoint in KNOWN_REASONING_PROVIDERS) defaultParamsEndpoint else endpoint
        if (resolutionEndpoint == AZURE_OPENAI) return null
        val resolved = resolveForModel(resolutionEndpoint, model, settings)
        return if (resolved != null && resolved.key in blockedKeys) null else submittable(resolved)
    }

    /** MIRRORED from upstream `isReasoningOverrideSupported`. */
    fun isSupported(override: ReasoningOverride, setting: ReasoningSetting?): Boolean {
        if (setting == null || setting.key != override.key) return false
        if (setting.isNumeric) return override.budgetValue != null
        val value = override.enumValue ?: return false
        return setting.options == null || value in setting.options
    }

    /** MIRRORED from upstream `resolveReasoningSetting`. */
    @Suppress("ReturnCount") // one early return per provider branch, as upstream reads
    private fun resolveForModel(endpoint: String, model: String, settings: List<ReasoningSetting>): ReasoningSetting? {
        fun find(key: String) = settings.firstOrNull { it.key == key }
        if (endpoint == GOOGLE) {
            if (EndpointParameterRegistry.hasGoogleThinkingBudget(model)) return find(ReasoningOverrideKeys.THINKING_BUDGET)
            if (GEMINI_THINKING_LEVEL.containsMatchIn(model)) return find(ReasoningOverrideKeys.THINKING_LEVEL)
            return null
        }
        val isAnthropic = endpoint == ANTHROPIC ||
            endpoint == "$BEDROCK-$ANTHROPIC" ||
            (endpoint == BEDROCK && modelKey(endpoint, model) == ANTHROPIC)
        if (isAnthropic) {
            if (EndpointParameterRegistry.supportsAdaptiveThinking(model)) return find(ReasoningOverrideKeys.EFFORT)
            if (MANUAL_CLAUDE_THINKING.containsMatchIn(model)) return find(ReasoningOverrideKeys.THINKING_BUDGET)
            return null
        }
        if (endpoint == AZURE_OPENAI) return find(ReasoningOverrideKeys.REASONING_EFFORT)
        if (endpoint == OPENAI) {
            return if (KNOWN_OPENAI_REASONING_MODEL.containsMatchIn(model)) find(ReasoningOverrideKeys.REASONING_EFFORT) else null
        }
        return ReasoningOverrideKeys.ALL.firstNotNullOfOrNull { find(it) }
    }

    /**
     * The per-model rules of upstream `applyModelAwareDefaults` that touch a reasoning key, then the
     * OpenRouter catalog narrowing (`narrowOpenRouterEfforts`).
     */
    private fun applyModelAwareDefaults(
        settings: List<ReasoningSetting>,
        defaultParamsEndpoint: String,
        model: String,
        catalog: CatalogEfforts,
    ): List<ReasoningSetting> {
        val grok = EndpointParameterRegistry.grok47Efforts(model)
        val family = settings.map { setting ->
            when {
                setting.key != ReasoningOverrideKeys.REASONING_EFFORT -> setting
                grok != null -> setting.copy(options = grok)
                EndpointParameterRegistry.isGpt6SolOrLuna(model) -> setting.copy(options = setting.options?.minus("minimal"))
                else -> setting
            }
        }
        if (defaultParamsEndpoint != OPENROUTER || catalog == CatalogEfforts.Generic) return family
        val reasoning = (catalog as? CatalogEfforts.Listed)?.reasoning
        val supported = reasoning?.efforts.orEmpty().filter { effort ->
            effort in REASONING_EFFORT_VALUES && !(reasoning?.mandatory == true && effort == "none")
        }
        return family.mapNotNull { setting ->
            when {
                setting.key != ReasoningOverrideKeys.REASONING_EFFORT -> setting
                supported.isEmpty() -> null
                else -> setting.copy(options = setting.options?.filter { it == "" || it in supported })
            }
        }
    }

    /** Narrows an enum setting to what the override schema accepts. MIRRORED from `toSubmittableReasoningSetting`. */
    private fun submittable(setting: ReasoningSetting?): ReasoningSetting? {
        val options = setting?.options ?: return setting
        val accepted = SCHEMA_VALUES[setting.key] ?: return setting
        val kept = options.filter { it in accepted }
        return when {
            kept.size == options.size -> setting
            kept.isEmpty() -> null
            else -> setting.copy(options = kept)
        }
    }

    /** An administrator's reasoning definition: only what it states replaces the built-in one. */
    private data class Declared(val key: String, val options: List<String>?, val type: String?) {
        /** Laid over [base] the way upstream spreads `{ ...setting, ...override }`. */
        fun over(base: ReasoningSetting?): ReasoningSetting {
            val fallback = base ?: BUILT_IN[key]
            return ReasoningSetting(
                key = key,
                options = options ?: fallback?.options,
                isNumeric = type?.let { it == "number" } ?: (fallback?.isNumeric ?: false),
                unsetIsOff = fallback?.unsetIsOff ?: false,
            )
        }
    }

    /** The administrator's own reasoning definitions, in declaration order. */
    private fun declaredReasoningSettings(customParams: CustomParams?): List<Declared> =
        customParams?.declaredParams.orEmpty()
            .filter { it.key in ReasoningOverrideKeys.ALL }
            .map { Declared(it.key, it.options, it.type) }
            .distinctBy { it.key }

    /** MIRRORED from upstream `getModelKey`: a Bedrock model's provider from its id. */
    private fun modelKey(endpoint: String, model: String): String {
        if (endpoint != BEDROCK) return model
        val parts = model.split('.')
        return parts.take(2).firstOrNull { it in BEDROCK_PROVIDERS } ?: parts[0]
    }

    private const val OPENAI = "openAI"
    private const val AZURE_OPENAI = "azureOpenAI"
    private const val ANTHROPIC = "anthropic"
    private const val GOOGLE = "google"
    private const val BEDROCK = "bedrock"
    private const val CUSTOM = "custom"
    private const val OPENROUTER = "openrouter"

    // MIRRORED from upstream `ReasoningEffort`, `AnthropicEffort` and `ThinkingLevel` (schemas.ts).
    private val REASONING_EFFORT_VALUES = listOf("", "none", "minimal", "low", "medium", "high", "xhigh", "max")
    private val ANTHROPIC_EFFORT_VALUES = listOf("", "low", "medium", "high", "xhigh", "max")
    private val THINKING_LEVEL_VALUES = listOf("", "minimal", "low", "medium", "high")

    private val SCHEMA_VALUES: Map<String, Set<String>> = mapOf(
        ReasoningOverrideKeys.REASONING_EFFORT to REASONING_EFFORT_VALUES.toSet(),
        ReasoningOverrideKeys.EFFORT to ANTHROPIC_EFFORT_VALUES.toSet(),
        ReasoningOverrideKeys.THINKING_LEVEL to THINKING_LEVEL_VALUES.toSet(),
    )

    private val OPENAI_EFFORT = ReasoningSetting(ReasoningOverrideKeys.REASONING_EFFORT, REASONING_EFFORT_VALUES)
    private val BEDROCK_EFFORT = ReasoningSetting(
        ReasoningOverrideKeys.REASONING_EFFORT,
        listOf("", "low", "medium", "high"),
        unsetIsOff = true,
    )
    private val ANTHROPIC_EFFORT = ReasoningSetting(ReasoningOverrideKeys.EFFORT, ANTHROPIC_EFFORT_VALUES)
    private val THINKING_BUDGET = ReasoningSetting(ReasoningOverrideKeys.THINKING_BUDGET, null, isNumeric = true)
    private val THINKING_LEVEL = ReasoningSetting(ReasoningOverrideKeys.THINKING_LEVEL, THINKING_LEVEL_VALUES)

    /** What a declared definition that names only its key falls back to. */
    private val BUILT_IN: Map<String, ReasoningSetting> = mapOf(
        ReasoningOverrideKeys.REASONING_EFFORT to OPENAI_EFFORT,
        ReasoningOverrideKeys.EFFORT to ANTHROPIC_EFFORT,
        ReasoningOverrideKeys.THINKING_LEVEL to THINKING_LEVEL,
        ReasoningOverrideKeys.THINKING_BUDGET to THINKING_BUDGET,
    )

    /**
     * MIRRORED from upstream `paramSettings` (parameterSettings.ts): each parameter set's reasoning
     * definitions, in list order. A set with none is still listed, since being listed is what
     * keeps an endpoint off the generic path.
     */
    private val SETTINGS: Map<String, List<ReasoningSetting>> = mapOf(
        OPENAI to listOf(OPENAI_EFFORT),
        AZURE_OPENAI to listOf(OPENAI_EFFORT),
        CUSTOM to listOf(OPENAI_EFFORT),
        OPENROUTER to listOf(OPENAI_EFFORT),
        ANTHROPIC to listOf(THINKING_BUDGET, ANTHROPIC_EFFORT),
        "$BEDROCK-anthropic" to listOf(THINKING_BUDGET, ANTHROPIC_EFFORT),
        "$BEDROCK-mistral" to emptyList(),
        "$BEDROCK-cohere" to emptyList(),
        "$BEDROCK-meta" to emptyList(),
        "$BEDROCK-ai21" to emptyList(),
        "$BEDROCK-amazon" to emptyList(),
        "$BEDROCK-deepseek" to emptyList(),
        "$BEDROCK-moonshot" to listOf(BEDROCK_EFFORT),
        "$BEDROCK-moonshotai" to listOf(BEDROCK_EFFORT),
        "$BEDROCK-openai" to emptyList(),
        "$BEDROCK-zai" to listOf(BEDROCK_EFFORT),
        GOOGLE to listOf(THINKING_BUDGET, THINKING_LEVEL),
    )

    /** MIRRORED from upstream `knownReasoningProviderEndpoints`. */
    private val KNOWN_REASONING_PROVIDERS = setOf(
        OPENAI,
        AZURE_OPENAI,
        ANTHROPIC,
        BEDROCK,
        "$BEDROCK-anthropic",
        "$BEDROCK-moonshot",
        "$BEDROCK-moonshotai",
        "$BEDROCK-zai",
    )

    /** MIRRORED from upstream `BedrockProviders`. */
    private val BEDROCK_PROVIDERS = setOf(
        "ai21", "amazon", "anthropic", "cohere", "deepseek", "meta", "mistral", "moonshot", "moonshotai",
        "openai", "stability", "zai",
    )

    // MIRRORED from upstream `isKnownOpenAIReasoningModel`, `isManualClaudeThinkingModel` and the
    // Gemini 3+/Gemma 4+ test in `resolveReasoningSetting`.
    private val KNOWN_OPENAI_REASONING_MODEL = Regex(
        """(?:^|[/._-])(?:o[134](?:[-.]|$)|gpt[-.]?(?:[5-9]|\d{2,})(?:[-.]|$)|gpt[-.]?oss(?:[-.]|$))""",
        RegexOption.IGNORE_CASE,
    )
    private val MANUAL_CLAUDE_THINKING = Regex(
        """claude-3[-.]7|claude-(?:sonnet|opus|haiku)-[4-9]|claude-[4-9](?:[-.][0-9]+)?-(?:sonnet|opus|haiku)""",
        RegexOption.IGNORE_CASE,
    )
    private val GEMINI_THINKING_LEVEL =
        Regex("""gemini-(?:[3-9]|\d{2,})|gemma-(?:[4-9]|\d{2,})""", RegexOption.IGNORE_CASE)
}
