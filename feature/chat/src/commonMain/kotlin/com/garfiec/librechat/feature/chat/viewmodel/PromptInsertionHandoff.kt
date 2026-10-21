package com.garfiec.librechat.feature.chat.viewmodel

/**
 * Carries prompt text from another navigation entry into a chat composer, unsent.
 *
 * Two producers, one consumer: the chat screen takes the text when it enters composition.
 * - The prompts library's "Use in chat" pops back, and the chat entry (whose ViewModel is still
 *   alive) re-enters.
 * - An agent page's conversation starter (staged by the nav host, since feature/agents can't see
 *   this type) opens `NewChat(agentId)`: a fresh landing composes for the first time, or the
 *   existing landing for that agent re-enters when the stack unwinds to it.
 *
 * Deliberately *not* routed through `DraftRepository`: drafts for an unsaved chat share the single
 * `__new_chat__` key across every conversation, so staging text there leaks between threads.
 *
 * Single-slot and take-once, mirroring [NewChatSelectionHandoff]. Text staged but never collected
 * dies with the process, which is the right failure mode — a prompt silently appearing in the
 * composer on next launch would be worse than losing it.
 */
class PromptInsertionHandoff {

    private var pending: String? = null

    fun put(text: String) {
        pending = text
    }

    /** Returns and clears the staged text, or null when nothing is waiting. */
    fun take(): String? {
        val current = pending
        pending = null
        return current
    }
}
