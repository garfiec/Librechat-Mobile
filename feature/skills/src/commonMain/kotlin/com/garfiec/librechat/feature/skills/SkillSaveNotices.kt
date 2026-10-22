package com.garfiec.librechat.feature.skills

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Hands the server's non-blocking `warnings` from a skill save (create or PATCH) to the page the
 * editor returns to, which shows them once. One per app (a Koin single), so the hand-off survives
 * the editor's ViewModel being cleared on pop.
 *
 * The detail page drains it for its skill on every resume (an edit returns there). The list page
 * drains all of it on every resume: a create returns there, and the list can't know the new id.
 */
class SkillSaveNotices {
    private val pending = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Queues [messages] for [skillId]'s page; blank messages are dropped. */
    fun post(skillId: String, messages: List<String>) {
        val shown = messages.map(String::trim).filter(String::isNotEmpty)
        if (shown.isEmpty()) return
        pending.update { it + (skillId to it[skillId].orEmpty() + shown) }
    }

    /** Takes and clears what is queued for [skillId]. */
    fun take(skillId: String): List<String> = pending.getAndUpdate { it - skillId }[skillId].orEmpty()

    /** Takes and clears everything queued, for any skill. */
    fun takeAll(): List<String> = pending.getAndUpdate { emptyMap() }.values.flatten()
}
