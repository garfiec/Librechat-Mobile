package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice

/**
 * The "Start new chats on: My last-used model" setting, applied to [resolveDefaultModelSpec]'s
 * answer. Mobile-only (web has no such preference), so it lives here rather than in the resolver,
 * which mirrors upstream.
 *
 * With [preferLastUsed] on, a prioritized spec is demoted to a soft default: the seeder then opens
 * the new chat on [lastUsed] when it is usable, and on the spec only when it isn't. The spec stays
 * prioritized where the server leaves the user no choice:
 * - [modelSelect] off: the admin hid the selector;
 * - no last-used selection to prefer. Under `enforce` [lastUsed] is never a raw pair
 *   ([seedableLastUsed]), so a raw last-used the server would refuse counts as none.
 */
internal fun SpecSeed?.demotedForLastUsed(
    preferLastUsed: Boolean,
    modelSelect: Boolean,
    lastUsed: ModelChoice?,
): SpecSeed? {
    if (this == null || !prioritized) return this
    if (!preferLastUsed || !modelSelect || lastUsed == null) return this
    return copy(prioritized = false)
}

/**
 * [resolveDefaultModelSpec]'s answer plus web's two ungated default-spec paths: the first load of
 * `/c/new` (`ChatRoute.tsx`, `getNewConvoPreset`) and the `defaultPreset` every later new chat starts
 * from (`useAppStartup.ts`, `useNewConvo`'s `switchToConversation`). Both take the `default: true`
 * spec whatever `prioritize` says, so it opens every new chat ahead of last-used, as a prioritized
 * spec does. "Start new chats on: My last-used model" then demotes it ([demotedForLastUsed]).
 */
internal fun SpecSeed?.withUngatedDefault(specs: ModelSpecs?): SpecSeed? =
    this ?: specs?.list.orEmpty().firstOrNull { it.isDefault }?.toSeed(prioritized = true)

/**
 * The stored [lastUsed] pick as the seeder may open it, or null. A last spec reopens on its current
 * pair, as the resolver reopens it. A raw pair on an endpoint `addedEndpoints` no longer lists can't
 * be picked again, so it never seeds and never outranks the server's spec; nor does any raw pair
 * under `enforce` ([requiresSpec]), which the server refuses, `addedEndpoints` or not. With
 * `interface.modelSelect` off the spec already wins (Tier 0b); where none applies, web falls back to
 * the last setup, so last-used still does here.
 */
internal fun ModelSpecs?.seedableLastUsed(lastUsed: LastUsedSelection): ModelChoice? {
    val endpoint = lastUsed.endpoint ?: return null
    val model = lastUsed.model ?: return null
    val pick = reopenedPick(endpoint, model, lastUsed.spec)
    return pick.takeIf { it is ModelChoice.Spec || (!requiresSpec && showsRawEndpoint(endpoint, modelSelect = true)) }
}

/**
 * The specs the seeder's Tier 1b tries in order, once last-used can't apply: [seed] while it is a
 * soft default, then, on a server that [requiresSpec], every spec with a pair, the `default` one
 * first.
 */
internal fun ModelSpecs?.fallbackSpecSeeds(seed: SpecSeed?): List<SpecSeed> {
    val soft = listOfNotNull(seed?.takeUnless { it.prioritized })
    if (!requiresSpec) return soft
    val list = this?.list.orEmpty()
    return soft + (list.filter { it.isDefault } + list.filterNot { it.isDefault }).mapNotNull { it.toSeed() }
}
