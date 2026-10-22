package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.config.ModelSpecSkills
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which skills the composer's picker offers: web's `$` popover filter plus specs, chips and the cap. */
class SkillPickingTest {

    private fun skill(
        id: String,
        name: String = id,
        active: Boolean = true,
        userInvocable: Boolean? = null,
        title: String = name,
    ) = CatalogSkill(
        id = id,
        name = name,
        title = title,
        description = null,
        isActive = active,
        userInvocable = userInvocable,
    )

    private fun names(
        catalog: List<CatalogSkill>?,
        canPick: Boolean = true,
        endpoint: String = "anthropic",
        selectedModel: String? = "claude",
        agents: List<Agent> = emptyList(),
        spec: ModelSpecSkills? = null,
        pending: List<String> = emptyList(),
    ) = pickableSkills(catalog, canPick, endpoint, selectedModel, agents, spec, pending).map { it.name }

    // ── the popover filter ───────────────────────────────────────────

    @Test
    fun onlySkillsActiveForTheUserAreOffered() {
        assertEquals(listOf("on"), names(listOf(skill("on"), skill("off", active = false))))
    }

    @Test
    fun onlyAnExplicitFalseHidesANonInvocableSkill() {
        val catalog = listOf(
            skill("unset", userInvocable = null),
            skill("yes", userInvocable = true),
            skill("model-only", userInvocable = false),
        )
        assertEquals(listOf("unset", "yes"), names(catalog))
    }

    @Test
    fun nothingWithoutTheGateOrBeforeTheCatalogLoads() {
        assertEquals(emptyList(), names(listOf(skill("a")), canPick = false))
        assertEquals(emptyList(), names(null))
        assertEquals(emptyList(), names(emptyList()))
    }

    @Test
    fun namesAreDedupedFirstOneWins() {
        // Two users' skills can share a name; the server resolves a pick by name.
        val catalog = listOf(skill("id-1", name = "pdf", title = "Mine"), skill("id-2", name = "pdf", title = "Theirs"))
        val offered = pickableSkills(catalog, true, "anthropic", "claude", emptyList(), null, emptyList())
        assertEquals(listOf("Mine"), offered.map { it.title })
    }

    @Test
    fun aStagedChipIsNotOfferedAgainAndTheCapStopsTheRows() {
        val catalog = listOf(skill("pdf"), skill("voice"))
        assertEquals(listOf("voice"), names(catalog, pending = listOf("pdf")))
        val full = (1..MAX_MANUAL_SKILLS).map { "s$it" }
        assertEquals(emptyList(), names(catalog, pending = full))
    }

    // ── model specs ──────────────────────────────────────────────────

    @Test
    fun aSpecThatTurnsSkillsOffOffersNone() {
        assertEquals(emptyList(), names(listOf(skill("pdf")), spec = ModelSpecSkills.Off))
    }

    @Test
    fun aSpecAllowlistLimitsByName() {
        val catalog = listOf(skill("id-1", name = "pdf"), skill("id-2", name = "voice"))
        assertEquals(listOf("voice"), names(catalog, spec = ModelSpecSkills.Only(listOf("voice"))))
        assertEquals(listOf("pdf", "voice"), names(catalog, spec = ModelSpecSkills.All))
    }

    // ── persisted agents ─────────────────────────────────────────────

    private val catalog = listOf(skill("id-a", name = "a"), skill("id-b", name = "b"))

    private fun agentChat(agent: Agent?) = names(
        catalog,
        endpoint = EndpointConstants.AGENTS,
        selectedModel = "agent_1",
        agents = listOfNotNull(agent),
    )

    private fun agent(enabled: Boolean?, skills: List<String>? = null, scope: String? = null) =
        Agent(id = "agent_1", skillsEnabled = enabled, skills = skills, skillsScope = scope)

    @Test
    fun anAgentNotLoadedOrMissingOffersNone() {
        assertEquals(emptyList(), agentChat(null))
    }

    @Test
    fun anAgentWithSkillsOffOffersNone() {
        assertEquals(emptyList(), agentChat(agent(enabled = null)))
        assertEquals(emptyList(), agentChat(agent(enabled = false, skills = listOf("id-a"))))
    }

    @Test
    fun aLegacyAgentWithAnEmptyAllowlistOffersTheCatalog() {
        assertEquals(listOf("a", "b"), agentChat(agent(enabled = true)))
        assertEquals(listOf("a", "b"), agentChat(agent(enabled = true, skills = emptyList())))
    }

    @Test
    fun aLegacyAgentAllowlistLimitsById() {
        assertEquals(listOf("b"), agentChat(agent(enabled = true, skills = listOf("id-b"))))
    }

    @Test
    fun anExplicitScopeWins() {
        assertEquals(emptyList(), agentChat(agent(enabled = true, skills = listOf("id-a"), scope = "none")))
        assertEquals(listOf("a", "b"), agentChat(agent(enabled = true, skills = listOf("id-a"), scope = "all")))
        assertEquals(listOf("a"), agentChat(agent(enabled = true, skills = listOf("id-a"), scope = "selected")))
        // Selected with nothing selected offers nothing, unlike the legacy empty allowlist.
        assertEquals(emptyList(), agentChat(agent(enabled = true, skills = emptyList(), scope = "selected")))
    }

    @Test
    fun agentScopeResolvesToNullForTheFullCatalog() {
        assertNull(agentSkillScope("agent_1", listOf(agent(enabled = true, scope = "all"))))
        assertEquals(emptySet(), agentSkillScope("agent_2", listOf(agent(enabled = true))))
    }

    @Test
    fun anEphemeralChatIgnoresAgentScopes() {
        // Not the agents endpoint: no persisted agent decides.
        assertEquals(listOf("a", "b"), names(catalog, agents = listOf(agent(enabled = false))))
    }

    // ── what a send may still carry ──────────────────────────────────

    private fun sendable(
        pending: List<String>,
        catalog: List<CatalogSkill>? = this.catalog,
        canPick: Boolean = true,
        endpoint: String = "anthropic",
        agents: List<Agent> = emptyList(),
        spec: ModelSpecSkills? = null,
    ) = sendableManualSkills(pending, catalog, canPick, endpoint, "agent_1", agents, spec)

    @Test
    fun aSendCarriesNoChipsWhereSkillsCantBePicked() {
        assertEquals(emptyList(), sendable(listOf("a"), canPick = false))
        assertEquals(emptyList(), sendable(listOf("a"), spec = ModelSpecSkills.Off))
    }

    @Test
    fun aSendKeepsOnlyTheSpecAllowlist() {
        assertEquals(listOf("b"), sendable(listOf("a", "b"), spec = ModelSpecSkills.Only(listOf("b"))))
    }

    @Test
    fun aSendKeepsOnlyChipsInsideTheAgentScope() {
        val scoped = listOf(agent(enabled = true, skills = listOf("id-b"), scope = "selected"))
        assertEquals(listOf("b"), sendable(listOf("a", "b"), endpoint = EndpointConstants.AGENTS, agents = scoped))
        val off = listOf(agent(enabled = false))
        assertEquals(emptyList(), sendable(listOf("a"), catalog = null, endpoint = EndpointConstants.AGENTS, agents = off))
        // An agent that isn't loaded can't be checked, so its chips pass.
        assertEquals(listOf("a"), sendable(listOf("a"), endpoint = EndpointConstants.AGENTS, agents = emptyList()))
    }

    @Test
    fun aSendDropsChipsTheUserTurnedOffOrThatAreGone() {
        val catalog = listOf(skill("id-a", name = "a", active = false), skill("id-b", name = "b"))
        assertEquals(listOf("b"), sendable(listOf("a", "b", "deleted"), catalog = catalog))
    }

    @Test
    fun withoutACatalogASendKeepsWhatItCantCheck() {
        assertEquals(listOf("a", "b"), sendable(listOf("a", "b"), catalog = null))
    }

    @Test
    fun chipsThatNeedTheCatalogWaitForOne() {
        val held = sendableManualSkills(
            listOf("a", "b"), null, true, "anthropic", "agent_1", emptyList(), null, needsCatalog = setOf("a"),
        )
        assertEquals(listOf("b"), held)
        // With a catalog they are checked like any other chip.
        val checked = sendableManualSkills(
            listOf("a", "b"), catalog, true, "anthropic", "agent_1", emptyList(), null, needsCatalog = setOf("a"),
        )
        assertEquals(listOf("a", "b"), checked)
    }

    // ── one set of rules ─────────────────────────────────────────────

    @Test
    fun thePickerAndTheSendRecheckAgreeSkillBySkill() {
        val catalog = listOf(
            skill("id-a", name = "a"),
            skill("id-b", name = "b", active = false),
            skill("id-c", name = "c", userInvocable = false),
            skill("id-d", name = "d"),
            skill("id-e", name = "e"),
        )
        val scoped = listOf(agent(enabled = true, skills = listOf("id-a", "id-b", "id-c", "id-e"), scope = "selected"))
        val spec = ModelSpecSkills.Only(listOf("a", "b", "c", "d"))
        val picked = pickableSkills(catalog, true, EndpointConstants.AGENTS, "agent_1", scoped, spec, emptyList())
            .map { it.name }
        val sent = sendable(
            catalog.map { it.name },
            catalog = catalog,
            endpoint = EndpointConstants.AGENTS,
            agents = scoped,
            spec = spec,
        )
        assertEquals(listOf("a"), picked)
        assertEquals(picked, sent)
    }

    @Test
    fun theRulesRefuseWhereNothingCanBePicked() {
        assertNull(skillPickRules(false, "anthropic", "claude", emptyList(), null, unloadedAgentRefuses = true))
        assertNull(skillPickRules(true, "anthropic", "claude", emptyList(), ModelSpecSkills.Off, unloadedAgentRefuses = true))
        // An agent that isn't loaded: the picker refuses, the send can't check its scope.
        assertNull(skillPickRules(true, EndpointConstants.AGENTS, "agent_9", emptyList(), null, unloadedAgentRefuses = true))
        assertEquals(
            true,
            skillPickRules(true, EndpointConstants.AGENTS, "agent_9", emptyList(), null, unloadedAgentRefuses = false)
                ?.allowsName("a"),
        )
    }

    @Test
    fun theRulesAllowOnlyTheSpecsNames() {
        val rules = skillPickRules(true, "anthropic", "claude", emptyList(), ModelSpecSkills.Only(listOf("a")), true)
        assertEquals(true, rules?.allowsName("a"))
        assertEquals(false, rules?.allowsName("b"))
        assertEquals(false, rules?.allowsName(""))
    }

    // ── catalog mapping ──────────────────────────────────────────────

    @Test
    fun theRowShowsTheDisplayTitleAndStagesTheName() {
        val summary = SkillSummary(id = "id-1", name = "pdf-tools", displayTitle = "PDF tools", description = " ")
        val row = summary.toCatalogSkill(active = true)
        assertEquals("PDF tools", row.title)
        assertEquals("pdf-tools", row.name)
        assertNull(row.description)
        assertEquals("pdf-tools", SkillSummary(id = "id-2", name = "pdf-tools", displayTitle = "").toCatalogSkill(true).title)
    }
}
