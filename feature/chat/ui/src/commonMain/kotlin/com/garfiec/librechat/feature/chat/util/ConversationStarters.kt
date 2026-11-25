package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.config.ModelSpec

/**
 * MIRRORED from upstream `Constants.MAX_CONVO_STARTERS` (`packages/data-provider/src/config.ts`),
 * registered in `scripts/mirrors.json` as `convo-starters`.
 */
internal const val MAX_CONVO_STARTERS = 4

/**
 * The starters the new-chat screen offers: the agent's own, else the selected spec's, capped at
 * [MAX_CONVO_STARTERS]. MIRRORED from web's `ConversationStarters.tsx` precedence.
 *
 * [agentStarters] is null while the agent's single-agent read hasn't resolved; nothing is offered
 * then, so an agent-backed spec doesn't show the spec's starters and swap them for the agent's a
 * round trip later.
 */
internal fun resolveLandingStarters(isAgent: Boolean, agentStarters: List<String>?, spec: ModelSpec?): List<String> {
    if (isAgent && agentStarters == null) return emptyList()
    return agentStarters.orEmpty().usable()
        .ifEmpty { spec?.conversationStarters.orEmpty().usable() }
        .take(MAX_CONVO_STARTERS)
}

private fun List<String>.usable(): List<String> = map(String::trim).filter(String::isNotEmpty)
