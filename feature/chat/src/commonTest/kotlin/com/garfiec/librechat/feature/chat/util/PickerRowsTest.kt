package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickerRowsTest {

    private fun spec(name: String, group: String? = null, endpoint: String = "openAI") =
        ModelSpec(name = name, label = name, preset = Preset(endpoint = endpoint, model = "gpt-4o"), group = group)

    private fun assertUniqueKeys(rows: List<PickerRow>) {
        val dupes = rows.groupBy { it.key }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate keys $dupes")
    }

    private fun List<PickerRow>.header(key: String) = filterIsInstance<PickerRow.Header>().single { it.key == key }

    // enforce + addedEndpoints [openAI]: "Fast" nests under openAI, "Writer" heads its own group,
    // and openAI's raw models show greyed.
    private val enforced = PickerInputs(
        endpointConfigs = mapOf("openAI" to EndpointConfig(modelDisplayLabel = "OpenAI")),
        availableModels = mapOf("openAI" to listOf("gpt-4o", "gpt-4o-mini")),
        modelSpecs = ModelSpecs(
            list = listOf(spec("General"), spec("Fast", group = "openAI"), spec("Writer", group = "Writing")),
            enforce = true,
            addedEndpoints = listOf("openAI"),
        ),
        expandedGroups = mapOf("ep:openAI" to true, "specgroup:Writing" to true),
    )

    @Test
    fun anAgentsKeyInTheModelMapDoesNotDuplicateTheAgentsHeader() {
        val rows = pickerRows(
            PickerInputs(
                availableModels = mapOf(EndpointConstants.AGENTS to listOf("a1")),
                endpointConfigs = mapOf(EndpointConstants.AGENTS to EndpointConfig()),
                agents = listOf(Agent(id = "a1", name = "Helper")),
                searchQuery = "Help",
            ),
        )
        assertUniqueKeys(rows)
        assertEquals(listOf("hdr:agents", "agent:a1"), rows.map { it.key })
    }

    @Test
    fun anEndpointNamedStarredDoesNotCollideWithTheStarredSection() {
        val inputs = PickerInputs(
            availableModels = mapOf("starred" to listOf("m1")),
            endpointConfigs = mapOf("starred" to EndpointConfig()),
            favoriteModelKeys = setOf("starred::m1"),
            expandedGroups = mapOf("starred" to true, "ep:starred" to true),
        )
        StarredModelsDisplay.entries.forEach { assertUniqueKeys(pickerRows(inputs.copy(starredDisplay = it))) }
    }

    @Test
    fun modelNamesContainingUnderscoresDoNotCollideAcrossEndpoints() {
        val rows = pickerRows(
            PickerInputs(
                availableModels = mapOf("a_b" to listOf("c"), "a" to listOf("b_c")),
                endpointConfigs = mapOf("a_b" to EndpointConfig(), "a" to EndpointConfig()),
                searchQuery = "c",
            ),
        )
        assertUniqueKeys(rows)
        assertEquals(2, rows.filterIsInstance<PickerRow.Model>().size)
    }

    @Test
    fun repeatedModelsAndAgentsDoNotRepeatRows() {
        val rows = pickerRows(
            PickerInputs(
                availableModels = mapOf("openAI" to listOf("gpt-4o", "gpt-4o")),
                endpointConfigs = mapOf("openAI" to EndpointConfig()),
                agents = listOf(Agent(id = "a1", name = "gpt helper"), Agent(id = "a1", name = "gpt helper")),
                searchQuery = "gpt",
            ),
        )
        assertUniqueKeys(rows)
        assertEquals(1, rows.filterIsInstance<PickerRow.Model>().size)
        assertEquals(1, rows.filterIsInstance<PickerRow.AgentRow>().size)
    }

    @Test
    fun aGroupedSpecAlsoStarredAndAGroupNamedLikeASectionDoNotCollide() {
        val inputs = PickerInputs(
            availableModels = mapOf("openAI" to listOf("gpt-4o")),
            endpointConfigs = mapOf("openAI" to EndpointConfig()),
            modelSpecs = ModelSpecs(list = listOf(spec("coder", group = "openAI"), spec("pinned", group = "starred"))),
            selectedSpec = "coder",
            favoriteSpecNames = setOf("coder", "pinned"),
            starredDisplay = StarredModelsDisplay.TOP,
            expandedGroups = mapOf("specgroup:starred" to true),
        )
        val rows = pickerRows(inputs)
        assertUniqueKeys(rows)
        assertEquals(2, rows.filterIsInstance<PickerRow.Spec>().count { it.spec.name == "coder" })
        assertEquals(HeaderLabel.Text("starred"), rows.header("hdr:specgroup:starred").label)
    }

    @Test
    fun theEnforceNoteComesOnceBeforeEveryRow() {
        val withStarred = enforced.copy(
            favoriteSpecNames = setOf("Fast"),
            favoriteModelKeys = setOf("openAI::gpt-4o"),
        )
        listOf(
            enforced,
            withStarred.copy(starredDisplay = StarredModelsDisplay.TOP),
            withStarred.copy(starredDisplay = StarredModelsDisplay.GROUPED, expandedGroups = mapOf("starred" to true)),
            enforced.copy(searchQuery = "gpt"),
        ).forEach { inputs ->
            val rows = pickerRows(inputs)
            assertEquals(PickerRow.SpecOnlyNote, rows.first())
            assertEquals(1, rows.count { it == PickerRow.SpecOnlyNote })
            assertUniqueKeys(rows)
        }
        // Every spec sendable under enforce, grouped or not, sits below it.
        val specs = pickerRows(enforced).filterIsInstance<PickerRow.Spec>().map { it.spec.name }
        assertEquals(listOf("General", "Fast", "Writer"), specs)
    }

    @Test
    fun noNoteWithoutGreyedRows() {
        // enforce without addedEndpoints lists no raw row.
        val specsOnly = enforced.copy(modelSpecs = enforced.modelSpecs!!.copy(addedEndpoints = emptyList()))
        assertFalse(pickerRows(specsOnly).contains(PickerRow.SpecOnlyNote))
        // Without enforce the raw rows can be sent.
        val open = enforced.copy(modelSpecs = enforced.modelSpecs!!.copy(enforce = false))
        assertFalse(pickerRows(open).contains(PickerRow.SpecOnlyNote))
        // A search that matches no raw row leaves none to grey.
        assertFalse(pickerRows(enforced.copy(searchQuery = "Writer")).any { it is PickerRow.Model || it is PickerRow.AgentRow })
        assertFalse(pickerRows(enforced.copy(searchQuery = "Writer")).contains(PickerRow.SpecOnlyNote))
    }

    @Test
    fun noNoteWhenTheOnlyRawEndpointNeedsAKey() {
        val rows = pickerRows(
            PickerInputs(
                endpointConfigs = mapOf("groq" to EndpointConfig(userProvide = true)),
                availableModels = mapOf("groq" to listOf("llama")),
                modelSpecs = ModelSpecs(list = listOf(spec("Fast", endpoint = "groq")), enforce = true, addedEndpoints = listOf("groq")),
                endpointKeyStates = mapOf("groq" to KeyState.Unset),
            ),
        )
        assertFalse(rows.contains(PickerRow.SpecOnlyNote))
    }

    @Test
    fun aKeyNeededSectionListsItsSpecsButNotItsModels() {
        val rows = pickerRows(
            PickerInputs(
                endpointConfigs = mapOf("groq" to EndpointConfig(userProvide = true)),
                availableModels = mapOf("groq" to listOf("llama")),
                modelSpecs = ModelSpecs(list = listOf(spec("Fast", group = "groq", endpoint = "groq"))),
                endpointKeyStates = mapOf("groq" to KeyState.Unset),
            ),
        )
        val header = rows.header("hdr:ep:groq")
        assertEquals(KeyHint.NEEDS_KEY, header.keyHint)
        assertEquals("groq", header.keyEndpoint)
        // Counts the rows under it: the spec, not the hidden model.
        assertEquals(1, header.count)
        assertEquals(listOf("hdr:ep:groq", "ep:groq:spec:Fast"), rows.map { it.key })
        assertEquals(KeyHint.NEEDS_KEY, rows.filterIsInstance<PickerRow.Spec>().single().keyHint)
    }

    @Test
    fun anEnforceGroupNamedAfterAnEndpointReadsTheEndpointLabel() {
        val rows = pickerRows(
            PickerInputs(
                endpointConfigs = mapOf("openAI" to EndpointConfig(modelDisplayLabel = "OpenAI"), "agents" to EndpointConfig()),
                availableModels = mapOf("openAI" to listOf("gpt-4o")),
                modelSpecs = ModelSpecs(
                    list = listOf(spec("Fast", group = "openAI"), spec("Helper", group = "agents"), spec("Writer", group = "Writing")),
                    enforce = true,
                ),
            ),
        )
        val openAI = rows.header("hdr:specgroup:openAI")
        assertEquals(HeaderLabel.Text("OpenAI"), openAI.label)
        assertEquals(HeaderIcon.Endpoint("openAI"), openAI.icon)
        val agents = rows.header("hdr:specgroup:agents")
        assertEquals(HeaderLabel.MyAgents, agents.label)
        assertEquals(HeaderIcon.Agents, agents.icon)
        val writing = rows.header("hdr:specgroup:Writing")
        assertEquals(HeaderLabel.Text("Writing"), writing.label)
        assertEquals(HeaderIcon.Folder, writing.icon)
    }

    @Test
    fun withModelSelectOffAGroupKeepsItsRawName() {
        val rows = pickerRows(
            PickerInputs(
                endpointConfigs = mapOf("openAI" to EndpointConfig(modelDisplayLabel = "OpenAI")),
                availableModels = mapOf("openAI" to listOf("gpt-4o")),
                modelSpecs = ModelSpecs(list = listOf(spec("Fast", group = "openAI"), spec("Helper", group = "agents"))),
                modelSelect = false,
            ),
        )
        assertEquals(HeaderLabel.Text("openAI"), rows.header("hdr:specgroup:openAI").label)
        assertEquals(HeaderLabel.Text("agents"), rows.header("hdr:specgroup:agents").label)
    }

    @Test
    fun aSearchListsSpecsFlatAndOpensEverySection() {
        val rows = pickerRows(enforced.copy(searchQuery = "g", expandedGroups = emptyMap()))
        assertTrue(rows.none { it.key.startsWith("hdr:specgroup:") })
        assertTrue(rows.filterIsInstance<PickerRow.Spec>().all { it.key.startsWith("spec:") })
        val header = rows.header("hdr:ep:openAI")
        assertTrue(header.expanded)
        assertEquals(null, header.expandKey)
        assertTrue(rows.filterIsInstance<PickerRow.Model>().isNotEmpty())
    }

    @Test
    fun theSelectedSpecsSectionStartsOpenAndOthersClosed() {
        val rows = pickerRows(enforced.copy(selectedSpec = "Writer", expandedGroups = emptyMap()))
        assertTrue(rows.header("hdr:specgroup:Writing").expanded)
        assertFalse(rows.header("hdr:ep:openAI").expanded)
        assertTrue(rows.none { it is PickerRow.Model })
    }
}
