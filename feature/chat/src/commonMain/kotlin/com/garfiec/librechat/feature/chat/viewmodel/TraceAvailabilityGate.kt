package com.garfiec.librechat.feature.chat.viewmodel

/**
 * Whether the trace entry point's availability is worth asking the server about.
 *
 * Pulled out of `observeTraceAvailability`'s collector because every input to it is a state
 * transition and the collector needs a live stream to reach — so the rule itself is otherwise
 * only assertable by driving a whole conversation.
 *
 * [alreadyShownFor] is what stops a settled turn re-confirming an affordance that is already on
 * screen: only false→true can change anything a user sees. Availability does not go back to
 * false for a conversation that has one, and the two ways the entry point legitimately
 * disappears — the interface flag going away, or the server being ruled out — are separate
 * inputs that clear it without coming through here.
 */
internal fun shouldResolveTraceAvailability(
    conversationId: String?,
    enabled: Boolean,
    isRuledOut: Boolean,
    isStreaming: Boolean,
    alreadyShownFor: String?,
): Boolean = when {
    conversationId == null || !enabled || isRuledOut -> false
    // Mid-run the trace cannot have gained the turn yet, and this is the one trace route with no
    // rate limiter in front of it.
    isStreaming -> false
    else -> conversationId != alreadyShownFor
}
