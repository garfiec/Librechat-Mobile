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

/**
 * The pair this spec opens on, by `preset.endpoint` alone, as upstream's `resolveModelSpecEndpoint`
 * (`packages/data-provider/src/models.ts`) reads it: an agents spec runs its `agent_id`, any other
 * its `model`, and a spec with no endpoint opens nothing. The server writes an endpoint it infers
 * from `agent_id` into the served config (`materializeModelSpecEndpoints`), so no inference here;
 * a stray `agent_id` on another endpoint is ignored, as the server ignores it.
 */
internal fun ModelSpec.toSeed(prioritized: Boolean = false): SpecSeed? {
    if (name.isBlank()) return null
    val preset = preset ?: return null
    val endpoint = preset.endpoint?.takeIf { it.isNotBlank() } ?: return null
    val model = (if (endpoint == EndpointConstants.AGENTS) preset.agentId else preset.model)
        ?.takeIf { it.isNotBlank() } ?: return null
    return SpecSeed(endpoint, model, prioritized, name)
}

/**
 * A stored pick (last-used, a model shortcut) reopened against the live config: the spec it names
 * on that spec's current pair, as web's `getDefaultModelSpec` reopens `LAST_CONVO_SETUP.spec`, while
 * the server offers one by that name; else the raw pair. A spec an admin moved to another model is
 * still the user's pick, so it follows the spec rather than falling back to the old raw model.
 *
 * Only for picks. A loaded conversation or preset keeps [offeredOn]: its spec and pair were sent
 * together, so a moved spec there is a different selection.
 */
internal fun ModelSpecs?.reopenedPick(endpoint: String, model: String, spec: String?): ModelChoice {
    val seed = spec?.let { name -> this?.list.orEmpty().firstOrNull { it.name == name }?.toSeed() }
    return seed?.toChoice() ?: ModelChoice.Raw(endpoint, model)
}

/**
 * Whether the picker lists [endpoint]'s raw models and agents next to the named specs. Mirrors
 * upstream's `useEndpoints` filter: nothing raw with `interface.modelSelect` off ([modelSelect]),
 * else only `addedEndpoints` when set. Under `enforce` with no `addedEndpoints`, nothing raw is
 * listed: web lists everything there, and every pick fails.
 */
internal fun ModelSpecs?.showsRawEndpoint(endpoint: String, modelSelect: Boolean): Boolean =
    modelSelect && (this == null || (addedEndpoints.isEmpty() && !enforce) || endpoint in addedEndpoints)

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

/** A start-chat entry point's pair: an agent card's agent, or a model shortcut's pair and the spec it was recorded from. */
internal data class StartChatPick(val endpoint: String, val model: String, val spec: String?, val shortcut: Boolean)

/**
 * What a start-chat entry point opens on once the spec config is known, or null when it opens
 * nothing. An agent card opens its agent, with the selector off too, as on web. An enforced server
 * refuses a send without a spec, so a raw pair adopts the spec when exactly one opens on it;
 * otherwise the send preflight asks.
 *
 * A model shortcut reopens the spec it was recorded from, on that spec's current pair, as a
 * last-used pick does ([reopenedPick]). Without one (a raw pick, or one recorded before shortcuts
 * kept their spec) its raw pair opens only where the picker lists that endpoint: a start-chat link
 * wins over the server's spec, so it must not open a raw model an admin hid. There the one spec on
 * the pair opens instead, or nothing when none or several do. Under `enforce` it takes the one
 * spec on its pair as any entry point does, and otherwise opens the raw pair for the send
 * preflight to ask.
 */
internal fun StartChatPick.resolvedAgainst(specs: ModelSpecs?, modelSelect: Boolean): ModelChoice? {
    val pair = endpoint to model
    if (!shortcut) return ModelChoice.Raw(endpoint, model).inferredFor(pair, specs)
    val reopened = specs.reopenedPick(endpoint, model, spec)
    if (reopened is ModelChoice.Spec) return reopened
    if (specs.requiresSpec || specs.showsRawEndpoint(endpoint, modelSelect)) return reopened.inferredFor(pair, specs)
    return specs.uniqueSpecFor(endpoint, model)?.let { ModelChoice.Spec(it, endpoint, model) }
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
 * The spec a reopened comparison's second lane was sent with, read back from its ephemeral agent
 * id. The server saves no spec for the added convo; the id's sender segment is
 * `modelLabel ?? spec.label ?? modelDisplayLabel` (`getEphemeralSender`, left out when empty), and
 * the client never sends a `modelLabel` for lane B, so a spec lane's sender is its label. The one
 * spec on [endpoint] and [model] with that label wins. Failing that, an enforced server, which
 * only a spec lane can have come from, takes the one spec on the pair ([uniqueSpecFor]); any other
 * server reopens the raw pair, since a raw lane with that pair is just as likely.
 */
internal fun ModelSpecs?.laneBSpecFor(endpoint: String, model: String, sender: String?): String? {
    val byLabel = sender?.let { label ->
        this?.list.orEmpty().singleOrNull { spec ->
            spec.label == label && spec.toSeed()?.let { it.endpoint == endpoint && it.model == model } == true
        }
    }
    return byLabel?.name ?: uniqueSpecFor(endpoint, model).takeIf { requiresSpec }
}

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
