package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.config.ModelSpecSkills
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import com.garfiec.librechat.feature.chat.model.PickerSkill

/**
 * A skill from the catalog with whether it is active for this user, resolved once when the
 * catalog loads (`resolveSkillActive`: the user's override, else the server's default rule).
 */
@Immutable
data class CatalogSkill(
    val id: String,
    val name: String,
    val title: String,
    val description: String?,
    val isActive: Boolean,
    /** `userInvocable` as the server stores it; only an explicit `false` hides a skill. */
    val userInvocable: Boolean?,
)

/** [this] summary as a [CatalogSkill], given whether it is [active] for the user. */
fun SkillSummary.toCatalogSkill(active: Boolean): CatalogSkill = CatalogSkill(
    id = id,
    name = name,
    title = displayTitle?.takeIf { it.isNotBlank() } ?: name,
    description = description?.takeIf { it.isNotBlank() },
    isActive = active,
    userInvocable = userInvocable,
)

/**
 * The skills a persisted agent lets a chat pick: null means no limit (the full catalog), an empty
 * set means none, otherwise the agent's allowlist of skill ids.
 *
 * MIRRORED from web `SkillsCommand.tsx` (`agentSkillIds`) over `resolveAgentSkillsScope`
 * (`packages/data-provider/src/schemas.ts`), registered in `scripts/mirrors.json` as
 * `skill-picker-agent-scope`. An agent that isn't in [agents] (still loading, deleted, or no
 * longer visible) offers none, as web fails closed on the same window. `skills_enabled` other
 * than `true` offers none. An explicit `skills_scope` wins: `none` offers none, `all` the full
 * catalog, anything else the allowlist. Without one, a non-empty allowlist limits and an empty
 * one means the full catalog (legacy agents).
 */
internal fun agentSkillScope(agentId: String?, agents: List<Agent>): Set<String>? {
    val agent = agents.firstOrNull { it.id == agentId } ?: return emptySet()
    if (agent.skillsEnabled != true) return emptySet()
    val allowlist = agent.skills.orEmpty()
    val scope = agent.skillsScope ?: if (allowlist.isNotEmpty()) SCOPE_SELECTED else SCOPE_ALL
    return when (scope) {
        SCOPE_NONE -> emptySet()
        SCOPE_ALL -> null
        else -> allowlist.toSet()
    }
}

private const val SCOPE_ALL = "all"
private const val SCOPE_SELECTED = "selected"
private const val SCOPE_NONE = "none"

/**
 * The rules that decide which skills may be picked, staged or sent in a chat. One instance serves
 * the picker's rows ([pickableSkills]), the recheck a send makes ([sendableManualSkills]) and a
 * chip being staged, so the three can't disagree.
 *
 * Filtered as web filters its `$` popover (`filterSkillsForPopover`, registered in
 * `scripts/mirrors.json` as `skill-picker-filter`): active for this user, `userInvocable` not
 * `false`, and in a persisted-agent chat only that agent's scope ([agentSkillScope]). Then the
 * active model spec's `skills` setting: [ModelSpecSkills.Only] allows only its names.
 */
class SkillPickRules internal constructor(
    /** The persisted agent's allowlist of skill ids, or null for no limit. */
    private val agentScope: Set<String>?,
    /** The model spec's allowlist of names, or null for no limit. */
    private val specNames: Set<String>?,
) {
    /** Whether a skill named [name] may be staged or sent: what can be checked without the catalog. */
    fun allowsName(name: String): Boolean = name.isNotEmpty() && (specNames == null || name in specNames)

    /** Whether the catalog's [skill] may be picked or sent here. */
    fun allows(skill: CatalogSkill): Boolean =
        skill.isActive &&
            skill.userInvocable != false &&
            (agentScope == null || skill.id in agentScope) &&
            allowsName(skill.name)
}

/**
 * The [SkillPickRules] for a chat, or null when no skill may be picked or sent there: [canPick]
 * (`ChatUiState.skillsAllowedHere`: role, capability, not assistants) is false, the spec turns skills
 * off ([ModelSpecSkills.Off]), or the persisted agent offers none.
 *
 * [unloadedAgentRefuses] is how an agent missing from [agents] reads: the picker fails closed, as
 * web does on the same window; a send lets it pass, because its scope just can't be checked and
 * the server still filters.
 */
@Suppress("LongParameterList")
fun skillPickRules(
    canPick: Boolean,
    endpoint: String,
    selectedModel: String?,
    agents: List<Agent>,
    specSkills: ModelSpecSkills?,
    unloadedAgentRefuses: Boolean,
): SkillPickRules? {
    if (!canPick || specSkills == ModelSpecSkills.Off) return null
    val checksAgent = endpoint == EndpointConstants.AGENTS &&
        (unloadedAgentRefuses || agents.any { it.id == selectedModel })
    val agentScope = if (checksAgent) agentSkillScope(selectedModel, agents) else null
    if (agentScope?.isEmpty() == true) return null
    return SkillPickRules(agentScope, (specSkills as? ModelSpecSkills.Only)?.names?.toSet())
}

/**
 * The skills the composer's picker offers right now, in catalog order: those [skillPickRules]
 * allows. Names aren't unique across users and the server resolves a pick by name, so rows are
 * deduped by name; a name already staged as a chip isn't offered again, and nothing is offered
 * once [MAX_MANUAL_SKILLS] chips are staged. A null [catalog] (not loaded) offers nothing.
 */
@Suppress("LongParameterList")
fun pickableSkills(
    catalog: List<CatalogSkill>?,
    canPick: Boolean,
    endpoint: String,
    selectedModel: String?,
    agents: List<Agent>,
    specSkills: ModelSpecSkills?,
    pending: List<String>,
): List<PickerSkill> {
    if (catalog.isNullOrEmpty() || pending.size >= MAX_MANUAL_SKILLS) return emptyList()
    val rules = skillPickRules(canPick, endpoint, selectedModel, agents, specSkills, unloadedAgentRefuses = true)
        ?: return emptyList()
    val seen = HashSet<String>()
    return catalog.asSequence()
        .filter { rules.allows(it) && it.name !in pending && seen.add(it.name) }
        .map { PickerSkill(name = it.name, title = it.title, description = it.description) }
        .toList()
}

/**
 * The staged chips in [pending] a send may still carry, in pick order: [skillPickRules] applied
 * again when the send takes them, because the endpoint, agent or model spec can change after a
 * chip was staged and the server then primes nothing for it while the user message still shows
 * its pill.
 *
 * With a [catalog], a name must still belong to a skill the rules allow. What can't be checked
 * (no catalog, an agent not in [agents]) passes; the server still filters it. The exception is
 * [needsCatalog]: names already held back once for a reason only the catalog shows (a skill
 * turned off, outside the agent's scope), which without a catalog stay held back.
 */
@Suppress("LongParameterList")
fun sendableManualSkills(
    pending: List<String>,
    catalog: List<CatalogSkill>?,
    canPick: Boolean,
    endpoint: String,
    selectedModel: String?,
    agents: List<Agent>,
    specSkills: ModelSpecSkills?,
    needsCatalog: Set<String> = emptySet(),
): List<String> {
    if (pending.isEmpty()) return emptyList()
    val rules = skillPickRules(canPick, endpoint, selectedModel, agents, specSkills, unloadedAgentRefuses = false)
        ?: return emptyList()
    val allowed = pending.filter(rules::allowsName)
    if (catalog == null) return allowed.filter { it !in needsCatalog }
    val stillPickable = catalog.asSequence().filter(rules::allows).mapTo(HashSet()) { it.name }
    return allowed.filter { it in stillPickable }
}
