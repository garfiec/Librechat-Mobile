package com.garfiec.librechat.core.data.skills

/**
 * Whether a skill the user never toggled is active for them: deployment and owned skills are on,
 * shared ones follow `interface.skills.defaultActiveOnShare`. Without a [userId] only deployment
 * skills are on, because the server fails closed when it has lost the user.
 *
 * Mirrors web `resolveSkillDefaultActive` (`useSkillActiveState.ts`) and the server's
 * `resolveSkillActive` (`packages/api/src/agents/skills.ts`) after its override step.
 */
fun resolveSkillDefaultActive(
    author: String?,
    source: String?,
    userId: String?,
    defaultActiveOnShare: Boolean,
): Boolean = when {
    source == SkillSource.DEPLOYMENT -> true
    userId.isNullOrBlank() -> false
    isSkillOwner(author, userId) -> true
    else -> defaultActiveOnShare
}

/**
 * Whether [skillId] is active for the user: an explicit override in [states] wins, otherwise
 * [resolveSkillDefaultActive]. The same rule the server applies when it builds a run's catalog.
 */
fun resolveSkillActive(
    skillId: String,
    author: String?,
    source: String?,
    states: Map<String, Boolean>,
    userId: String?,
    defaultActiveOnShare: Boolean,
): Boolean = states[skillId] ?: resolveSkillDefaultActive(author, source, userId, defaultActiveOnShare)

/**
 * The full states map to POST after setting [skillId] to [active]. A value equal to the default
 * removes the key instead of storing it, so the map stays a list of exceptions: the server caps it
 * at 200 entries, and round-trip toggles would otherwise fill it.
 */
fun nextSkillStates(
    states: Map<String, Boolean>,
    skillId: String,
    active: Boolean,
    defaultActive: Boolean,
): Map<String, Boolean> =
    if (active == defaultActive) states - skillId else states + (skillId to active)
