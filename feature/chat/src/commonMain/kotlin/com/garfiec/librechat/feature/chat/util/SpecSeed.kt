package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice

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
    val spec: String,
)

/**
 * Port of upstream's `getDefaultModelSpec` (`client/src/utils/endpoints.ts`) combined with the
 * `shouldApplyModelSpec` gate in `useNewConvo.ts`, which decides whether its answer is used.
 * [modelSelect] is `interface.modelSelect`, true when the server leaves it out.
 *
 * [lastSpec] is upstream's stored `LAST_CONVO_SETUP.spec`: the spec name of the user's last pick,
 * null when that pick was a raw model. "Yields to the user's prior selection" for a soft default
 * with no stored spec is decided by the seeder (a usable last-used wins), not here.
 */
internal fun resolveDefaultModelSpec(specs: ModelSpecs?, modelSelect: Boolean, lastSpec: String?): SpecSeed? {
    if (specs == null || specs.list.isEmpty()) return null
    val prioritized = specs.prioritize == true || !modelSelect

    val hard = specs.list.firstOrNull { it.isDefault }
    if (hard != null) {
        // Upstream returns it either way, but only applies it under these conditions.
        return if (prioritized) hard.toSeed(prioritized = true) else null
    }
    val soft = specs.list.firstOrNull { it.softDefault }
    // Upstream applies a stored spec on every new chat, whatever `prioritize` says.
    val last = lastSpec?.let { name -> specs.list.firstOrNull { it.name == name } }
    if (last != null && last.name != soft?.name) {
        last.toSeed(prioritized = true)?.let { return it }
    }
    if (soft != null) {
        // With the selector off there is no prior selection to yield to. `prioritize` does not
        // upgrade a soft default: upstream checks the soft branch first.
        return soft.toSeed(prioritized = !modelSelect)
    }
    return if (prioritized) specs.list.first().toSeed(prioritized = true) else null
}

internal fun SpecSeed.toChoice(): ModelChoice = ModelChoice.Spec(spec, endpoint, model)

/**
 * A stored selection (a conversation, preset, last-used pick or staged Compare pick) as a [ModelChoice]:
 * a spec when it names one and has a model to pair it with, else raw. The spec is not checked
 * against the config here; see [offeredOrUnchecked] and [withoutStale].
 */
internal fun ModelChoice.Companion.restore(endpoint: String, model: String?, spec: String?): ModelChoice =
    if (spec != null && model != null) ModelChoice.Spec(spec, endpoint, model) else ModelChoice.Raw(endpoint, model)

internal fun ModelSpec.toSeed(prioritized: Boolean = false): SpecSeed? {
    if (name.isBlank()) return null
    val preset = preset ?: return null
    val agentId = preset.agentId?.takeIf { it.isNotBlank() }
    if (agentId != null) return SpecSeed(EndpointConstants.AGENTS, agentId, prioritized, name)
    val endpoint = preset.endpoint?.takeIf { it.isNotBlank() } ?: return null
    val model = preset.model?.takeIf { it.isNotBlank() } ?: return null
    return SpecSeed(endpoint, model, prioritized, name)
}

/**
 * Whether the picker lists [endpoint]'s raw models and agents next to the named specs. Mirrors
 * the `addedEndpoints` filter in upstream's `useEndpoints`. Under `enforce` with no
 * `addedEndpoints`, nothing raw is listed: web lists everything there, and every pick fails.
 */
internal fun ModelSpecs?.showsRawEndpoint(endpoint: String): Boolean =
    this == null || (addedEndpoints.isEmpty() && !enforce) || endpoint in addedEndpoints

/**
 * Whether the server rejects any send that names no spec (`buildEndpointOption.js`). It has no
 * exception for `addedEndpoints`, so raw rows shown under it can't be sent to.
 */
internal val ModelSpecs?.requiresSpec: Boolean
    get() = this != null && enforce && list.isNotEmpty()

/**
 * This stored spec name, while the server still offers that spec on the same endpoint and model;
 * else null. A renamed or retargeted spec leaves the raw pair, since sending its name with another
 * endpoint fails with "Model spec mismatch".
 */
internal fun String?.offeredOn(endpoint: String, model: String, specs: ModelSpecs?): String? =
    this?.takeIf { name ->
        specs?.list.orEmpty().any {
            val seed = it.toSeed()
            seed?.spec == name && seed.endpoint == endpoint && seed.model == model
        }
    }

/**
 * The selection for this stored spec name on [endpoint] and [model]: the spec while
 * [offeredOn] holds, or unchecked while there is nothing to check it against ([checkable] false:
 * the spec config has not loaded; the seeder re-checks it when the config arrives). Else raw.
 */
internal fun String?.offeredOrUnchecked(
    endpoint: String,
    model: String?,
    specs: ModelSpecs?,
    checkable: Boolean,
): ModelChoice {
    val spec = if (checkable) model?.let { offeredOn(endpoint, it, specs) } else this
    return ModelChoice.restore(endpoint, model, spec)
}

/** This choice without a spec [specs] no longer offers on its pair. */
internal fun ModelChoice.withoutStale(specs: ModelSpecs?): ModelChoice =
    if (this is ModelChoice.Spec && name.offeredOn(endpoint, model, specs) == null) ModelChoice.Raw(endpoint, model) else this

/**
 * This raw choice as the one spec [uniqueSpecFor] finds for it, while it still holds the [tier0]
 * raw pair an entry point asked for and [specs] are enforced.
 */
internal fun ModelChoice.inferredFor(tier0: Pair<String, String>?, specs: ModelSpecs?): ModelChoice {
    val (endpoint, model) = tier0 ?: return this
    if (this !is ModelChoice.Raw || this.endpoint != endpoint || this.model != model) return this
    if (!specs.requiresSpec) return this
    return ModelChoice.restore(endpoint, model, specs.uniqueSpecFor(endpoint, model))
}

/**
 * The one spec that opens on [endpoint] and [model], for an entry point that names only the raw
 * pair (an agent card, a model shortcut) on a server that [requiresSpec]. Null when no spec or
 * more than one matches: the user picks, rather than the client guessing between them.
 */
internal fun ModelSpecs?.uniqueSpecFor(endpoint: String, model: String): String? =
    this?.list.orEmpty()
        .mapNotNull { it.toSeed() }
        .filter { it.endpoint == endpoint && it.model == model }
        .singleOrNull()
        ?.spec

/**
 * The spec rows the picker lists, ascending by `order` with unordered specs after them in list
 * order. An agent spec is left out once [agentsLoaded] shows its agent is not among [agents], as
 * web's `ModelSelectorContext` does; until then every spec shows. So does every spec while
 * [agents] is a full page: web loads every page, but mobile fetches one, and an agent past it
 * still exists.
 */
internal fun ModelSpecs?.visibleSpecs(agents: List<Agent>, agentsLoaded: Boolean): List<ModelSpec> {
    val complete = agentsLoaded && agents.size < AGENTS_LIST_PAGE_SIZE
    val agentIds = if (complete) agents.mapTo(HashSet()) { it.id } else null
    return this?.list.orEmpty()
        .filter { spec ->
            val seed = spec.toSeed() ?: return@filter false
            spec.showInMenu != false &&
                (agentIds == null || seed.endpoint != EndpointConstants.AGENTS || seed.model in agentIds)
        }
        .sortedBy { it.order ?: Double.POSITIVE_INFINITY }
}

/** The page `/api/agents` returns when asked for no limit, which is all `AgentRepository.getAgents` fetches. */
private const val AGENTS_LIST_PAGE_SIZE = 100
