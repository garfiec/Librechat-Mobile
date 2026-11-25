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
    bareBedrockClaudeIsAnthropic: Boolean = false,
    thinkingLevelXhighSupported: Boolean = false,
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
        customParams = config?.reasoningCustomParams(model),
        blockedKeys = enforcedPreset?.reasoningKeys().orEmpty(),
        catalog = efforts,
        bareClaudeIsAnthropic = bareBedrockClaudeIsAnthropic,
        thinkingLevelXhighSupported = thinkingLevelXhighSupported,
    )
}

/** The reasoning keys a model spec's preset sets: an enforced spec locks them (upstream `blockedReasoningKeys`). */
private fun Preset.reasoningKeys(): Set<String> = buildSet {
    if (reasoningEffort != null) add(ReasoningOverrideKeys.REASONING_EFFORT)
    if (effort != null) add(ReasoningOverrideKeys.EFFORT)
    if (thinkingLevel != null) add(ReasoningOverrideKeys.THINKING_LEVEL)
    if (thinkingBudget != null) add(ReasoningOverrideKeys.THINKING_BUDGET)
}

/** The parameter that switches thinking on or off; `false` outranks an inherited level ([branchReasoning]). */
const val THINKING_SWITCH_KEY = "thinking"

/** Override keys whose level also turns thinking on, so `thinking: false` outranks an inherited one. */
private val THINKING_LEVEL_KEYS = setOf(
    ReasoningOverrideKeys.EFFORT,
    ReasoningOverrideKeys.THINKING_LEVEL,
    ReasoningOverrideKeys.THINKING_BUDGET,
)

/**
 * The override on the nearest user message of [path] that has one (upstream `getBranchReasoning`):
 * the level a fresh turn extending [path] inherits on a server that carries it down the branch.
 */
fun nearestBranchReasoning(path: List<MessageNode>): ReasoningOverride? =
    path.asReversed().firstNotNullOfOrNull { node ->
        node.message.reasoningOverride?.takeIf { node.message.isCreatedByUser }
    }

/**
 * The inherited level [nearest] ([nearestBranchReasoning]), or null when [thinkingOff] and that level
 * would turn thinking back on, as the server skips it then.
 */
fun branchReasoning(nearest: ReasoningOverride?, thinkingOff: Boolean): ReasoningOverride? =
    nearest?.takeUnless { thinkingOff && it.key in THINKING_LEVEL_KEYS }

/**
 * The composer's Thinking chip and sheet: the levels [setting] takes, the chat's own value for it
 * ([configured], `""` when unset), the level the branch already runs at ([inherited]) and the next
 * message's choice ([pending]).
 */
@Immutable
data class ThinkingChip(
    val key: String,
    val options: List<String>,
    val configured: String,
    val pending: String?,
    /** The empty value means "no reasoning" here rather than "the provider decides". */
    val unsetIsOff: Boolean,
    /** The level a turn with no pick inherits from its branch; null when none, or on a server that doesn't. */
    val inherited: String? = null,
    /** A pick holds for the rest of the conversation, since the server inherits it; else for one message. */
    val lastsForConversation: Boolean = false,
) {
    /** What the next message will use. */
    val shown: String get() = pending ?: inherited ?: configured

    /** The next message runs at a level other than the chat's own setting. */
    val departs: Boolean get() = shown != configured

    /** The sheet offers a way back: a level held for the conversation is not the chat's own setting. */
    val canReturnToChatSetting: Boolean get() = lastsForConversation && departs && configured in options
}

/**
 * The chip for [setting], or null. Levels only: a token budget (`thinkingBudget`) gets no chip,
 * and neither does a setting with no levels. [branch] counts only when [lastsForConversation] and
 * the setting still takes it, as the server skips an inherited level the target no longer runs.
 */
fun thinkingChip(
    setting: ReasoningSetting?,
    configured: String,
    pending: ReasoningOverride?,
    branch: ReasoningOverride? = null,
    lastsForConversation: Boolean = false,
): ThinkingChip? {
    if (setting == null || setting.isNumeric) return null
    val options = setting.options?.takeIf { it.isNotEmpty() } ?: return null
    return ThinkingChip(
        key = setting.key,
        options = options,
        configured = configured,
        pending = pending?.takeIf { it.key == setting.key }?.enumValue,
        unsetIsOff = setting.unsetIsOff,
        inherited = branch?.takeIf { lastsForConversation && ReasoningSettingResolver.isSupported(it, setting) }?.enumValue,
        lastsForConversation = lastsForConversation,
    )
}

/**
 * What picking [value] in the sheet stages: nothing when it is the level the next message runs at
 * without a pick (the inherited one, else the chat's own), else an override. Going back to the
 * chat's own setting under an inherited level is an explicit override: sending none inherits.
 */
fun ThinkingChip.overrideFor(value: String): ReasoningOverride? =
    if (value == (inherited ?: configured)) null else ReasoningOverride.level(key, value)
