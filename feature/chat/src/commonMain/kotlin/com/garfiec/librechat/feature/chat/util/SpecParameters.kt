package com.garfiec.librechat.feature.chat.util

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
 * - off a spec, onto a raw model: the defaults, so a spec's values (a `reasoning_effort`, a 32k
 *   `max_tokens`) don't follow the user onto a model that may refuse them.
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
internal fun ModelSelectionState.withChoice(choice: ModelChoice, specs: ModelSpecs? = modelSpecs): ModelSelectionState =
    copy(
        choice = choice,
        modelSpecs = specs,
        modelParameters = modelParameters.acrossSpecChange(this.choice, choice, specs),
        specTools = specTools.takeIf { choice.spec != null && choice.spec == this.choice.spec },
    )

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
