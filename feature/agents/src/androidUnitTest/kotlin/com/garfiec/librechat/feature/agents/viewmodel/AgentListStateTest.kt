package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.feature.agents.AgentCardDisplayData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which state the index's list area shows, and when the New agent FAB steps aside. */
class AgentListStateTest {

    private val agent = AgentCardDisplayData(
        id = "agent-1",
        name = "Coding Assistant",
        description = null,
        avatarUrl = null,
        categoryValue = null,
        contactName = null,
        isPromoted = false,
    )

    private fun answered(chip: AgentChip, search: String = "", agents: List<AgentCardDisplayData> = emptyList()) =
        AgentMarketplaceUiState(
            agents = agents,
            isLoading = false,
            selectedChip = chip,
            searchQuery = search,
            shownQuery = AgentListQuery(chip, search),
        )

    @Test
    fun `All with no search and nothing back is the empty server, with no FAB`() {
        val state = answered(AgentChip.All)
        assertThat(state.listState()).isEqualTo(AgentListState.ServerEmpty)
        assertThat(state.showsNewAgentFab).isFalse()
    }

    @Test
    fun `an empty Top picks with no search offers Show all agents`() {
        val state = answered(AgentChip.TopPicks)
        assertThat(state.listState()).isEqualTo(AgentListState.NoMatches(AgentChip.TopPicks, AgentListWayOut.ShowAll))
        assertThat(state.showsNewAgentFab).isTrue()
    }

    @Test
    fun `Top picks while searching searches everything, so the way out is Clear search`() {
        val state = answered(AgentChip.TopPicks, search = "payroll")
        assertThat(state.listState()).isEqualTo(AgentListState.NoMatches(AgentChip.TopPicks, AgentListWayOut.ClearSearch))
    }

    @Test
    fun `All while searching offers Clear search`() {
        assertThat(answered(AgentChip.All, search = "payroll").listState())
            .isEqualTo(AgentListState.NoMatches(AgentChip.All, AgentListWayOut.ClearSearch))
    }

    @Test
    fun `a category while searching offers Search all agents`() {
        val chip = AgentChip.Category("finance")
        assertThat(answered(chip, search = "payroll").listState())
            .isEqualTo(AgentListState.NoMatches(chip, AgentListWayOut.SearchAll))
    }

    @Test
    fun `a category or Editable with no search offers Show all agents`() {
        val finance = AgentChip.Category("finance")
        assertThat(answered(finance).listState()).isEqualTo(AgentListState.NoMatches(finance, AgentListWayOut.ShowAll))
        assertThat(answered(AgentChip.Editable).listState())
            .isEqualTo(AgentListState.NoMatches(AgentChip.Editable, AgentListWayOut.ShowAll))
    }

    @Test
    fun `Editable while searching offers Search all agents`() {
        assertThat(answered(AgentChip.Editable, search = "sql").listState())
            .isEqualTo(AgentListState.NoMatches(AgentChip.Editable, AgentListWayOut.SearchAll))
    }

    @Test
    fun `a failed first load is the error page, not the empty server`() {
        val state = answered(AgentChip.All).copy(loadError = AgentListError(serverMessage = "Database unavailable"))
        assertThat(state.listState()).isEqualTo(AgentListState.Error("Database unavailable"))
        assertThat(state.showsNewAgentFab).isTrue()
    }

    @Test
    fun `a failed reload with agents on screen keeps the agents`() {
        val state = answered(AgentChip.All, agents = listOf(agent)).copy(loadError = AgentListError(serverMessage = null))
        assertThat(state.listState()).isEqualTo(AgentListState.Agents)
    }

    @Test
    fun `a first load, or a pull on the error page, shows placeholders`() {
        assertThat(AgentMarketplaceUiState().listState()).isEqualTo(AgentListState.Placeholders)
        val pulling = answered(AgentChip.All).copy(isRefreshing = true)
        assertThat(pulling.listState()).isEqualTo(AgentListState.Placeholders)
        assertThat(pulling.showsNewAgentFab).isTrue()
    }

    @Test
    fun `nothing answered yet claims no empty server`() {
        val state = AgentMarketplaceUiState(isLoading = false)
        assertThat(state.listState()).isEqualTo(AgentListState.Placeholders)
        assertThat(state.showsNewAgentFab).isTrue()
    }

    @Test
    fun `agents on screen are listed, even under a newer loading chip`() {
        val state = answered(AgentChip.All, agents = listOf(agent)).copy(isLoading = true)
        assertThat(state.listState()).isEqualTo(AgentListState.Agents)
    }

    @Test
    fun `the FAB needs AGENTS CREATE and browsing`() {
        val listed = answered(AgentChip.All, agents = listOf(agent))
        assertThat(listed.showsNewAgentFab).isTrue()
        assertThat(listed.copy(agentsCreateEnabled = false).showsNewAgentFab).isFalse()
        assertThat(listed.copy(marketplaceEnabled = false).showsNewAgentFab).isFalse()
    }
}
