package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.StartupConfig

/**
 * The selection a server's model spec asks new chats to open on.
 *
 * [prioritized] specs open every new chat, ahead of the user's last-used model. The others are
 * soft defaults: they apply only when the user has no usable selection of their own.
 */
internal data class SpecSeed(
    val endpoint: String,
    val model: String,
    val prioritized: Boolean,
)

/**
 * Port of upstream's `getDefaultModelSpec` (`client/src/utils/endpoints.ts`) combined with the
 * `shouldApplyModelSpec` gate in `useNewConvo.ts`, which decides whether its answer is used.
 *
 * Upstream's `last` branch (a stored spec name) has no counterpart: this client persists a
 * last-used (endpoint, model) pair and never a spec name, so that case is the seeder's last-used
 * tier. For the same reason, "yields to the user's prior selection" for a soft default is decided
 * by the seeder (a usable last-used wins), not here.
 */
internal fun resolveDefaultModelSpec(config: StartupConfig?): SpecSeed? {
    val specs = config?.modelSpecs ?: return null
    if (specs.list.isEmpty()) return null
    val modelSelect = config.interfaceConfig?.modelSelect ?: true
    val prioritized = specs.prioritize == true || !modelSelect

    val hard = specs.list.firstOrNull { it.isDefault }
    if (hard != null) {
        // Upstream returns it either way, but only applies it under these conditions.
        return if (prioritized) hard.toSeed(prioritized = true) else null
    }
    val soft = specs.list.firstOrNull { it.softDefault }
    if (soft != null) {
        // With the selector off there is no prior selection to yield to. `prioritize` does not
        // upgrade a soft default: upstream checks the soft branch first.
        return soft.toSeed(prioritized = !modelSelect)
    }
    return if (prioritized) specs.list.first().toSeed(prioritized = true) else null
}

private fun ModelSpec.toSeed(prioritized: Boolean): SpecSeed? {
    val preset = preset ?: return null
    val agentId = preset.agentId?.takeIf { it.isNotBlank() }
    if (agentId != null) return SpecSeed(EndpointConstants.AGENTS, agentId, prioritized)
    val endpoint = preset.endpoint?.takeIf { it.isNotBlank() } ?: return null
    val model = preset.model?.takeIf { it.isNotBlank() } ?: return null
    return SpecSeed(endpoint, model, prioritized)
}
