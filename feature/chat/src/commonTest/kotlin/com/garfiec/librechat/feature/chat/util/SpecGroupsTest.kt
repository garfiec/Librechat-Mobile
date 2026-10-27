package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.media.IconSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpecGroupsTest {

    private fun spec(name: String, group: String? = null, groupIcon: String? = null, order: Double? = null) =
        ModelSpec(name = name, preset = Preset(endpoint = "openAI", model = "gpt-5"), group = group, groupIcon = groupIcon, order = order)

    private fun names(specs: List<ModelSpec>) = specs.map { it.name }

    @Test
    fun splitsUngroupedEndpointAndCustomGroups() {
        val sections = specSections(
            listOf(spec("a"), spec("b", group = "openAI"), spec("c", group = "Research"), spec("d", group = " ")),
            shownEndpoints = setOf("openAI"),
        )
        assertEquals(listOf("a", "d"), names(sections.ungrouped))
        assertEquals(listOf("b"), names(sections.byEndpoint.getValue("openAI")))
        assertEquals(listOf("Research"), sections.custom.map { it.name })
        assertEquals(listOf("c"), names(sections.custom.single().specs))
    }

    @Test
    fun theFirstGroupIconWins() {
        val sections = specSections(
            listOf(
                spec("a", group = "R"),
                spec("b", group = "R", groupIcon = "first.png"),
                spec("c", group = "R", groupIcon = "second.png"),
            ),
            shownEndpoints = emptySet(),
        )
        assertEquals("first.png", sections.custom.single().icon)
    }

    @Test
    fun aGroupNamingAnEndpointNotShownIsItsOwnGroup() {
        // modelSelect: false shows no endpoint, so every group is a custom one, as on web.
        val sections =
            specSections(listOf(spec("a", group = "openAI"), spec("b", group = "agents")), shownEndpoints = emptySet())
        assertEquals(listOf("openAI", "agents"), sections.custom.map { it.name })
        assertEquals(emptyMap(), sections.byEndpoint)
    }

    @Test
    fun matchingIsExactAndCaseSensitive() {
        val sections = specSections(listOf(spec("a", group = "openai")), shownEndpoints = setOf("openAI"))
        assertEquals(listOf("openai"), sections.custom.map { it.name })
    }

    @Test
    fun theOrderSortCarriesIntoEachGroup() {
        val specs = ModelSpecs(
            list = listOf(spec("late", group = "R", order = 2.0), spec("early", group = "R", order = 1.0), spec("top", order = 3.0)),
        ).visibleSpecs(emptyList(), agentsLoaded = false)
        val sections = specSections(specs, emptySet())
        assertEquals(listOf("early", "late"), names(sections.custom.single().specs))
        assertEquals(listOf("top"), names(sections.ungrouped))
    }

    @Test
    fun sectionOfNamesTheSelectedSpecsSection() {
        val sections = specSections(
            listOf(spec("a"), spec("b", group = "openAI"), spec("c", group = "R")),
            shownEndpoints = setOf("openAI"),
        )
        assertNull(sections.sectionOf("a"))
        assertNull(sections.sectionOf(null))
        assertEquals(SpecSection.Endpoint("openAI"), sections.sectionOf("b"))
        assertEquals(SpecSection.Custom("R"), sections.sectionOf("c"))
    }

    @Test
    fun aSpecIconFallsBackToItsAgentsAvatarAndHidesWhenAsked() {
        val agentSpec = ModelSpec(name = "s", preset = Preset(endpoint = "agents", agentId = "a1"))
        val agents = listOf(Agent(id = "a1", avatar = kotlinx.serialization.json.JsonPrimitive("https://av/a.png")))
        assertEquals(IconSource("agents", "https://av/a.png"), agentSpec.iconSource(agents, "https://srv"))
        assertEquals(IconSource("agents", null), agentSpec.iconSource(emptyList(), "https://srv"))
        assertNull(agentSpec.copy(showIconInMenu = false).menuIconSource(agents, "https://srv"))
    }
}
