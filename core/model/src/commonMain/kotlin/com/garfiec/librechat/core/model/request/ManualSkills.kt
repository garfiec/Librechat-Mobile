package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.common.EndpointConstants

/**
 * Most skills one turn may name in [ChatRequest.manualSkills].
 *
 * MIRRORED from `MAX_MANUAL_SKILLS` in `packages/api/src/agents/skills.ts` (registered in
 * `scripts/mirrors.json` as `max-manual-skills`). The server truncates past it after deduping,
 * so a longer list is not an error: the extra picks are silently not primed. Capping the chips
 * here keeps the user from picking a skill that would never run.
 */
const val MAX_MANUAL_SKILLS = 10

/**
 * Whether [endpoint] takes manual skills: everything except the assistants endpoints, which
 * upstream's `createPayload` strips them from (`isAssistantsEndpoint`). Same spelling as the
 * quotes guard in feature/chat (`quotesSupportedOn`).
 */
fun manualSkillsSupportedOn(endpoint: String?): Boolean =
    !endpoint.equals("assistants", ignoreCase = true) &&
        !endpoint.equals("azureAssistants", ignoreCase = true)

/**
 * The list a turn sends: blanks dropped, deduplicated by name (names, not ids, are what the server
 * resolves, and two same-name skills resolve to one), capped at [MAX_MANUAL_SKILLS]. Mirrors the
 * server's own `extractManualSkills` + `resolveManualSkills` filtering, so the request carries
 * exactly what will be primed.
 */
fun normalizeManualSkills(names: List<String>?): List<String> =
    names.orEmpty().filter { it.isNotEmpty() }.distinct().take(MAX_MANUAL_SKILLS)

/**
 * The ephemeral agent a turn sends once its [manualSkills] are taken into account.
 *
 * An ephemeral run primes manual skills only when its skills flag is on
 * (`resolveAgentScopedSkillIds` scopes an ephemeral agent to nothing otherwise; registered in
 * `scripts/mirrors.json` as `manual-skills-ephemeral-flag`), so a non-agent turn that names skills
 * turns the flag on itself rather than trusting the composer toggle. A persisted agent's own
 * `skills_enabled` governs and it carries no ephemeral agent; assistants take no skills at all.
 *
 * The one place the rule lives: the request body and the resume pin both go through it, because
 * the resume route fingerprints `ephemeralAgent` and refuses (403) a body that differs from the one
 * the paused run was started with.
 */
fun EphemeralAgent?.withManualSkillsFlag(endpoint: String?, manualSkills: List<String>?): EphemeralAgent? {
    val sends = manualSkillsSupportedOn(endpoint) && normalizeManualSkills(manualSkills).isNotEmpty()
    if (!sends || endpoint == EndpointConstants.AGENTS) return this
    return (this ?: EphemeralAgent()).copy(skills = true)
}
