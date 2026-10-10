package com.garfiec.librechat.feature.chat.tchat

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TchatPickerModelTest {

    /** The two endpoints of deploy/tchat/librechat.yaml, as `/api/models` lists them. */
    private val tensorGrid = listOf(
        "gpt-5.6-terra",
        "gpt-5.6-sol",
        "gpt-5.6-luna",
        "gpt-5.5",
        "gpt-5.4",
        "gpt-5.4-mini",
        "claude-opus-5",
        "claude-sonnet-5",
        "claude-haiku-4-5",
        "deepseek-v4-flash",
        "deepseek-ai/deepseek-v4-pro-0813",
        "moonshotai/kimi-k3",
        "minimaxai/minimax-m3",
        "nvidia/nemotron-3-super-120b-a12b",
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "Qwen/Qwen3.6-35B-A3B-FP8",
        "Qwen3.8-27B",
        "gpt-image-2",
        "text-embedding-3-small",
    )
    private val tensorGridClaude = listOf(
        "claude-opus-5",
        "claude-sonnet-5",
        "claude-fable-5",
        "claude-opus-4-8",
        "claude-opus-4-7",
        "claude-opus-4-6",
        "claude-sonnet-4-6",
        "claude-haiku-4-5",
    )
    private val models = linkedMapOf("TensorGrid" to tensorGrid, "TensorGrid-Claude" to tensorGridClaude)
    private val labels = PickerLabels(imageGen = "Image Gen", agents = "Agents", agentCaption = "Agent", free = "Free")
    private val catalog = ModelCatalogView(
        TchatModelCatalog(free = listOf("deepseek-v4-flash", "gpt-image-2"), categories = mapOf("gpt-image-2" to "image")),
    )

    private fun build(
        availableModels: Map<String, List<String>> = models,
        selectedEndpoint: String? = "TensorGrid",
        selectedModel: String? = "gpt-5.6-luna",
        favoriteModelKeys: Set<String> = emptySet(),
        favoriteAgentIds: Set<String> = emptySet(),
        agents: List<Agent> = emptyList(),
        imageGen: ImageGenSelection? = null,
        showImageGen: Boolean = true,
    ) = buildTchatPicker(
        availableModels = availableModels,
        agents = agents,
        catalog = catalog,
        selectedEndpoint = selectedEndpoint,
        selectedModel = selectedModel,
        favoriteModelKeys = favoriteModelKeys,
        favoriteAgentIds = favoriteAgentIds,
        imageSpecs = ModelOwnersTest.IMAGE_SPECS,
        imageGen = imageGen,
        showImageGen = showImageGen,
        labels = labels,
    )

    private fun TchatPicker.group(id: String) = groups.first { it.id == id }

    @Test
    fun `makers come in display order, then Image Gen, then agents`() {
        val picker = build(agents = listOf(Agent(id = "agent_1", name = "Research")))
        assertEquals(
            listOf("OPENAI", "ANTHROPIC", "DEEPSEEK", "MOONSHOT", "QWEN", "MINIMAX", "NVIDIA", IMAGE_GROUP_ID, AGENTS_GROUP_ID),
            picker.groups.map { it.id },
        )
    }

    @Test
    fun `a model served by two endpoints is listed once, on the dedicated one`() {
        val claude = build().group("ANTHROPIC").rows
        assertEquals(8, claude.size)
        assertTrue(claude.all { (it.action as PickerAction.Select).endpoint == "TensorGrid-Claude" })
        // Whichever order the endpoints arrive in.
        val reversed = build(
            availableModels = linkedMapOf("TensorGrid-Claude" to tensorGridClaude, "TensorGrid" to tensorGrid),
        )
        assertTrue(reversed.group("ANTHROPIC").rows.all { (it.action as PickerAction.Select).endpoint == "TensorGrid-Claude" })
    }

    @Test
    fun `models that can't chat never appear as chat models`() {
        val ids = build().groups.filter { it.kind == PickerGroupKind.MAKER }.flatMap { it.rows }.map { it.detail }
        assertFalse("gpt-image-2" in ids)
        assertFalse("text-embedding-3-small" in ids)
        assertEquals(8, build().group("OPENAI").rows.size)
    }

    @Test
    fun `rows read as labels with the raw id and a Free tag`() {
        val row = build().group("DEEPSEEK").rows.first { it.detail == "deepseek-v4-flash" }
        assertEquals("DeepSeek V4 Flash", row.label)
        assertTrue(row.free)
        assertTrue("free" in row.searchText)
    }

    @Test
    fun `the current card names the model and its maker`() {
        val current = build().current!!
        assertEquals("GPT-5.6 Luna", current.label)
        assertEquals("OpenAI", current.caption)
        assertEquals("gpt-5.6-luna", current.detail)
        assertEquals("OPENAI", current.groupId)
        assertTrue(build().group("OPENAI").rows.single { it.selected }.detail == "gpt-5.6-luna")
    }

    @Test
    fun `an agent selection is current under agents and selects no model`() {
        val picker = build(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = "agent_1",
            agents = listOf(Agent(id = "agent_1", name = "Research")),
        )
        assertEquals("Research", picker.current!!.label)
        assertEquals(AGENTS_GROUP_ID, picker.current!!.groupId)
        assertTrue(picker.groups.filter { it.kind == PickerGroupKind.MAKER }.flatMap { it.rows }.none { it.selected })
        assertTrue(picker.group(AGENTS_GROUP_ID).rows.single().selected)
    }

    @Test
    fun `Image Gen marks the active entry and leaves the chat model selected`() {
        val off = build()
        assertNull(off.activeImage)
        assertTrue(off.group(IMAGE_GROUP_ID).rows.none { it.selected })

        val on = build(imageGen = ImageGenSelection("gemini-image"))
        assertEquals("gemini-image", on.activeImage?.name)
        assertEquals(listOf("image::gemini-image"), on.group(IMAGE_GROUP_ID).rows.filter { it.selected }.map { it.key })
        assertEquals("GPT-5.6 Luna", on.current!!.label)

        // The default selection marks the default entry.
        assertEquals("gpt-image", build(imageGen = ImageGenSelection()).activeImage?.name)
    }

    @Test
    fun `the Image Gen group is hidden when it can't be carried`() {
        val picker = build(showImageGen = false, imageGen = ImageGenSelection())
        assertTrue(picker.groups.none { it.id == IMAGE_GROUP_ID })
        assertNull(picker.activeImage)
    }

    @Test
    fun `pinned rows keep pin order and use the listed endpoint`() {
        val picker = build(
            favoriteModelKeys = linkedSetOf("TensorGrid::gpt-5.4", "TensorGrid-Claude::claude-opus-5", "TensorGrid::gone"),
            favoriteAgentIds = linkedSetOf("agent_1"),
            agents = listOf(Agent(id = "agent_1", name = "Research")),
        )
        assertEquals(listOf("agents::agent_1", "TensorGrid::gpt-5.4", "TensorGrid-Claude::claude-opus-5"), picker.pinned.map { it.key })
        assertEquals(true, picker.group("OPENAI").rows.first { it.detail == "gpt-5.4" }.pinned)
    }

    @Test
    fun `search matches label, id, maker, family and Free across groups`() {
        val groups = build().groups
        assertEquals(listOf("ANTHROPIC"), filterPickerGroups(groups, ALL_GROUPS, "claude").map { it.id }.distinct())
        assertEquals(8, filterPickerGroups(groups, ALL_GROUPS, "Anthropic").single().rows.size)
        assertEquals(listOf("Kimi K3"), filterPickerGroups(groups, ALL_GROUPS, "moonshot").flatMap { it.rows }.map { it.label })
        val free = filterPickerGroups(groups, ALL_GROUPS, "free").flatMap { it.rows }.map { it.detail }
        assertEquals(listOf("deepseek-v4-flash", "gpt-image-2"), free)
        assertTrue(filterPickerGroups(groups, "OPENAI", "claude").isEmpty())
        assertEquals(listOf("OPENAI"), filterPickerGroups(groups, "OPENAI", "").map { it.id })
    }
}
