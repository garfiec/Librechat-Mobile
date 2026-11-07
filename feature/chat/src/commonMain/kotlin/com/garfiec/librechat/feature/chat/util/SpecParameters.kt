package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.parameterPreset
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.delegate.mergedFromPreset

/**
 * The parameters a chat opens with on [spec]: its served preset over the defaults, as web copies
 * `{...spec.preset, spec}` into the conversation (`useSelectMention.ts`, `getModelSpecPreset`).
 * Without `enforce` the server applies none of the preset's public fields itself
 * (`mergeModelSpecPreset`), so these are what makes a spec chat run as the admin set it. The
 * private fields (`promptPrefix`, `instructions`, …) are never served and the server fills them in.
 *
 * Web search and URL context stay at their defaults: here they are also the tools toolbar's
 * toggles, which under a spec come from its tools and the preset's own ([presetTools]) instead.
 * Null when the spec isn't listed.
 */
internal fun ModelSpecs?.parametersFor(spec: String?): ModelParameters? {
    val preset = this?.list?.firstOrNull { it.name == spec }?.preset ?: return null
    return ModelParameters.DEFAULT.mergedFromPreset(preset).copy(
        webSearch = ModelParameters.DEFAULT.webSearch,
        urlContext = ModelParameters.DEFAULT.urlContext,
    )
}

/**
 * The parameters after the selection moves from [from] to [to]. Only a change of spec touches them,
 * so a tweak survives a re-pick of the same spec and every config refresh:
 * - onto a spec: that spec's preset ([parametersFor]);
 * - off a spec, onto a raw model the user picked: the defaults, so a spec's values (a
 *   `reasoning_effort`, a 32k `max_tokens`) don't follow the user onto a model that may refuse them.
 *   A config that drops the spec doesn't move the chat this way ([withSpecsRefreshed]).
 *
 * Under `enforce` a spec's preset applies on the same spec too: the server sends it in place of
 * whatever the chat holds, and nothing can be tweaked on top of it.
 *
 * The user's web search and URL context toggles carry across either way; under a spec the toolbar
 * shows the spec's tools instead ([SpecTools]) and leaves these for when the user moves off it.
 */
internal fun ModelParameters.acrossSpecChange(from: ModelChoice, to: ModelChoice, specs: ModelSpecs?): ModelParameters {
    val enforced = specs.requiresSpec && to.spec != null
    if (from.spec == to.spec && !enforced) return this
    val next = specs.parametersFor(to.spec) ?: if (from.spec == to.spec) return this else ModelParameters.DEFAULT
    return next.copy(webSearch = webSearch, urlContext = urlContext)
}

/**
 * This selection on [choice], with its parameters moved across the change of spec ([acrossSpecChange])
 * and the tools added on top of the previous spec dropped when the spec changes.
 */
internal fun ModelSelectionState.withChoice(
    choice: ModelChoice,
    specs: ModelSpecs? = modelSpecs,
    from: ModelChoice = this.choice,
): ModelSelectionState =
    copy(
        choice = choice,
        modelSpecs = specs,
        modelParameters = modelParameters.acrossSpecChange(from, choice, specs),
        specTools = specTools.takeIf { choice.spec != null && choice.spec == this.choice.spec },
    )

/**
 * This selection on a refreshed spec config, held as the server holds a stored spec
 * ([storedAgainst]), or, for the seeder's own landing pick ([seeded]), only while the spec is still
 * offered on its pair ([seededAgainst]). A dropped spec goes by name only: the endpoint and model are
 * unchanged, so the parameters stay (a later move to another pair resets them; see
 * `ModelSelectionDelegate.applySelection`). Any other change goes through [withChoice], which
 * re-applies an enforced spec's preset, including on the pair an enforced spec moved to.
 */
internal fun ModelSelectionState.withSpecsRefreshed(specs: ModelSpecs?, seeded: Boolean = false): ModelSelectionState {
    val current = choice.heldAgainst(specs, seeded)
    return if (choice.spec != null && current.spec == null) {
        copy(choice = current, modelSpecs = specs, specTools = null)
    } else {
        withChoice(current, specs)
    }
}

/**
 * The parameters [this] conversation was last sent with, as web restores them when it opens one. They
 * win over its spec's preset; a value it never stored is the default.
 */
internal fun Conversation.storedParameters(): ModelParameters = ModelParameters.DEFAULT.mergedFromPreset(parameterPreset())

/**
 * What a reopened [conversation] runs on: its [storedParameters], except under `enforce`, where the
 * server sends its spec's preset in their place, so that is what the read-only sheet shows.
 *
 * On a spec, web search and URL context stay as they are: what the chat stored there was the spec's
 * tools ([SpecTools]), not the user's own toggles, which these keep for when the user leaves it.
 */
internal fun ModelSelectionState.reopenedParameters(conversation: Conversation): ModelParameters {
    val stored = conversation.storedParameters()
    val toggles = if (choice.spec != null) modelParameters else stored
    val base = modelSpecs.parametersFor(choice.spec)?.takeIf { modelSpecs.requiresSpec } ?: stored
    return base.copy(webSearch = toggles.webSearch, urlContext = toggles.urlContext)
}

/**
 * A spec a config refresh dropped from the selection, with the selection it left behind (its raw
 * pair) and the parameters it had then, still the spec's. The next move off that pair counts as
 * leaving the spec ([acrossSpecChange]) rather than raw to raw, so a removed spec's preset doesn't
 * follow onto another model; back onto the same spec (re-seeded on its new pair) is a move onto it.
 * Used once, and only while neither the selection nor the parameters changed since: any other write
 * makes them the user's or another writer's.
 */
internal class DroppedSpecMemo {
    private var left: ModelChoice.Spec? = null
    private var onto: ModelChoice? = null
    private var parameters: ModelParameters? = null

    /** Remembers the spec [before] held if the refresh to [after] dropped it. */
    fun record(before: ModelSelectionState, after: ModelSelectionState) {
        val spec = before.choice as? ModelChoice.Spec ?: return
        if (after.choice.spec != null) return
        left = spec
        onto = after.choice
        parameters = after.modelParameters
    }

    /** The spec to move off instead of [previous]'s raw choice, for a move to [choice]; null when none applies. */
    fun take(previous: ModelSelectionState, choice: ModelChoice): ModelChoice.Spec? {
        val spec = left?.takeIf {
            onto == previous.choice &&
                parameters == previous.modelParameters &&
                (previous.choice.endpoint != choice.endpoint || previous.choice.model != choice.model) &&
                it.name != choice.spec
        }
        left = null
        onto = null
        parameters = null
        return spec
    }
}

/**
 * The parameters Compare's lane B ran on, for continuing the chat on it ([choice]), read from lane A's
 * selection (this one): lane B's spec's preset, or the defaults for a raw lane, as
 * `ComparisonModeDelegate.buildAddedConvo` sends the added convo only its spec's preset numbers. It
 * also carried lane A's custom instructions, except where either lane is on the agents endpoint, so
 * those stay where it did. The user's web search and URL context toggles carry across.
 */
internal fun ModelSelectionState.parametersLaneRanOn(choice: ModelChoice): ModelParameters {
    val ran = modelSpecs.parametersFor(choice.spec) ?: ModelParameters.DEFAULT
    val sentInstructions = choice.endpoint != EndpointConstants.AGENTS && selectedEndpoint != EndpointConstants.AGENTS
    return ran.copy(
        customInstructions = if (sentInstructions) modelParameters.customInstructions else ran.customInstructions,
        webSearch = modelParameters.webSearch,
        urlContext = modelParameters.urlContext,
    )
}
