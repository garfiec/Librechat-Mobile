package com.garfiec.librechat.feature.chat.util

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
 * - no last-used selection to prefer;
 * - an enforced server ([requiresSpec]) and a raw last-used pair, which the server would refuse.
 *   A last-used spec is still preferred there.
 */
internal fun SpecSeed?.demotedForLastUsed(
    preferLastUsed: Boolean,
    modelSelect: Boolean,
    lastUsed: ModelChoice?,
    specs: ModelSpecs?,
): SpecSeed? {
    if (this == null || !prioritized) return this
    if (!preferLastUsed || !modelSelect || lastUsed == null) return this
    if (specs.requiresSpec && lastUsed is ModelChoice.Raw) return this
    return copy(prioritized = false)
}
