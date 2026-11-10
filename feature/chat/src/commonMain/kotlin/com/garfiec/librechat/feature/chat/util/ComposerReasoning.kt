package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.ReasoningOverrideKeys
import com.garfiec.librechat.core.ui.components.CatalogEfforts
import com.garfiec.librechat.core.ui.components.ReasoningSetting
import com.garfiec.librechat.core.ui.components.ReasoningSettingResolver
import com.garfiec.librechat.feature.chat.viewmodel.ReasoningCatalogState

/** The first server line that takes a one-turn `reasoningOverride` (and serves the catalog route). */
const val REASONING_OVERRIDE_MIN_VERSION = "0.8.9-rc1"

/**
 * Which endpoint and model the composer's reasoning control resolves against: the selection, or for
 * a saved agent its provider and model. Null while an agent's are not resolved yet.
 */
data class ReasoningTarget(
    /** The endpoint's own name (the key `/api/config` and the catalog route use). */
    val endpoint: String,
    val model: String?,
)

fun reasoningTarget(
    selectedEndpoint: String,
    selectedModel: String?,
    agentProvider: String?,
    agentModel: String?,
): ReasoningTarget? = if (selectedEndpoint == EndpointConstants.AGENTS) {
    agentProvider?.let { ReasoningTarget(it, agentModel) }
} else {
    ReasoningTarget(selectedEndpoint, selectedModel)
}

/**
 * The reasoning control the composer may offer for one turn (upstream `useComposerReasoning`), or
 * null when there is none: a server older than v0.8.9-rc1 (or of unknown version), parameters off
 * (`interface.parameters`, which model specs default off), an assistants endpoint, a comparison, a
 * target that does not resolve, a key an enforced spec's preset sets, or an enforced spec the config
 * doesn't serve ([enforcedSpecUnserved]: its locked keys can't be known, and the server refuses an
 * override of one).
 */
@Suppress("LongParameterList")
fun composerReasoningSetting(
    backendVersion: String?,
    parametersEnabled: Boolean,
    endpointSupportsIt: Boolean,
    comparing: Boolean,
    target: ReasoningTarget?,
    endpointConfigs: Map<String, EndpointConfig>,
    catalog: ReasoningCatalogState?,
    enforcedPreset: Preset?,
    enforcedSpecUnserved: Boolean = false,
): ReasoningSetting? {
    val supported = backendVersion?.let { BackendVersion.isCompatibleOrNewer(it, REASONING_OVERRIDE_MIN_VERSION) } == true
    if (!supported || !parametersEnabled || !endpointSupportsIt || comparing || target == null) return null
    if (enforcedSpecUnserved) return null
    val model = target.model ?: return null
    val config = endpointConfigs[target.endpoint]
    val customParams = config?.customParams
    val efforts = if (ReasoningSettingResolver.usesCatalog(customParams)) {
        CatalogEfforts.of(catalog?.takeIf { it.endpoint == target.endpoint }?.models, model)
    } else {
        CatalogEfforts.Generic
    }
    return ReasoningSettingResolver.resolve(
        endpoint = config?.type ?: target.endpoint,
        model = model,
        customParams = customParams,
        blockedKeys = enforcedPreset?.reasoningKeys().orEmpty(),
        catalog = efforts,
    )
}

/** The reasoning keys a model spec's preset sets: an enforced spec locks them (upstream `blockedReasoningKeys`). */
private fun Preset.reasoningKeys(): Set<String> = buildSet {
    if (reasoningEffort != null) add(ReasoningOverrideKeys.REASONING_EFFORT)
    if (effort != null) add(ReasoningOverrideKeys.EFFORT)
    if (thinkingLevel != null) add(ReasoningOverrideKeys.THINKING_LEVEL)
    if (thinkingBudget != null) add(ReasoningOverrideKeys.THINKING_BUDGET)
}

/**
 * The composer's Thinking chip and sheet: the levels [setting] takes, the chat's own value for it
 * ([configured], `""` when unset) and the next message's choice ([pending]).
 */
@Immutable
data class ThinkingChip(
    val key: String,
    val options: List<String>,
    val configured: String,
    val pending: String?,
    /** The empty value means "no reasoning" here rather than "the provider decides". */
    val unsetIsOff: Boolean,
) {
    /** What the next message will use. */
    val shown: String get() = pending ?: configured
}

/**
 * The chip for [setting], or null. Levels only: a token budget (`thinkingBudget`) gets no chip,
 * and neither does a setting with no levels.
 */
fun thinkingChip(setting: ReasoningSetting?, configured: String, pending: ReasoningOverride?): ThinkingChip? {
    if (setting == null || setting.isNumeric) return null
    val options = setting.options?.takeIf { it.isNotEmpty() } ?: return null
    return ThinkingChip(
        key = setting.key,
        options = options,
        configured = configured,
        pending = pending?.takeIf { it.key == setting.key }?.enumValue,
        unsetIsOff = setting.unsetIsOff,
    )
}

/**
 * What picking [value] in the sheet stages: nothing when it is the chat's own setting (picking that
 * clears the choice), else a one-turn override.
 */
fun ThinkingChip.overrideFor(value: String): ReasoningOverride? =
    if (value == configured) null else ReasoningOverride.level(key, value)
