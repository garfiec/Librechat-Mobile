package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.model.PickerMcpServer
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.HeaderLabel
import com.garfiec.librechat.feature.chat.util.ModelPick
import com.garfiec.librechat.feature.chat.util.PickerModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatInputParsingTest {

    // ── Trigger boundaries ───────────────────────────────────────────────

    /** The `/` query, asserted alike where a skill can be picked and where none can. */
    private fun slashQuery(text: String): String? {
        val withSkills = parsePickerQuery(text, canPickSkills = true)
        val withoutSkills = parsePickerQuery(text, canPickSkills = false)
        assertEquals(withSkills, withoutSkills, "`/` must not depend on skills for \"$text\"")
        return withSkills?.takeIf { it.scope == null }?.query
    }

    @Test
    fun bareSlashOpensWithAnEmptyQuery() {
        assertEquals("", slashQuery("/"))
    }

    @Test
    fun textAfterSlashIsTheQuery() {
        assertEquals("summ", slashQuery("/summ"))
    }

    @Test
    fun spaceClosesThePicker() {
        // How the user dismisses without deleting what they typed.
        assertNull(slashQuery("/summ arize"))
        assertNull(slashQuery("/ "))
    }

    @Test
    fun slashMustLeadTheInput() {
        // Otherwise dates, paths and fractions would all open the picker mid-sentence.
        assertNull(slashQuery("what is 1/2"))
        assertNull(slashQuery("see docs/readme"))
        assertNull(slashQuery(" /summ"))
    }

    @Test
    fun emptyInputDoesNotTrigger() {
        assertNull(slashQuery(""))
    }

    // ── Filtering ────────────────────────────────────────────────────────

    private fun group(
        id: String,
        name: String,
        oneliner: String? = null,
        command: String? = null,
        promptText: String? = null,
    ) = PromptMentionDisplayData(
        id = id,
        name = name,
        command = command,
        oneliner = oneliner,
        category = null,
        promptText = promptText,
    )

    /** The prompt rows `/` lists for [query] over [prompts], by id. */
    private fun promptIds(query: String, prompts: List<PromptMentionDisplayData>): List<String> =
        buildPickerRows(PickerQuery(scope = null, query = query), prompts, emptyList())
            .map { (it as ComposerPickerItem.Prompt).prompt.id }

    @Test
    fun groupsWithoutACommandStillMatch() {
        // The server's list projection omits `command`; requiring one leaves the picker
        // permanently empty.
        val groups = listOf(group("1", "Summarize Question", command = null))
        assertEquals(listOf("1"), promptIds("summ", groups))
    }

    @Test
    fun matchesOnOneliner() {
        val groups = listOf(group("1", "Untitled", oneliner = "turns notes into a haiku"))
        assertEquals(listOf("1"), promptIds("haiku", groups))
    }

    @Test
    fun matchesOnPromptBodyWhenThereIsNoOneliner() {
        val groups = listOf(group("1", "Untitled", promptText = "rewrite this as a haiku"))
        assertEquals(listOf("1"), promptIds("haiku", groups))
    }

    @Test
    fun onelinerIsPreferredOverBodyForTheLabel() {
        val groups = listOf(
            group("1", "Untitled", oneliner = "short summary", promptText = "a haiku about cats"),
        )
        // The body isn't part of the label when a oneliner exists, mirroring the web client.
        assertEquals(emptyList(), promptIds("haiku", groups))
    }

    @Test
    fun anExactNameOutranksAPromptThatMerelyStartsWithIt() {
        // Web's second key is `command ?? name`; the label key can only reach STARTS_WITH, so
        // without the name fallback these two tie.
        val groups = listOf(
            group("longer", "Summarize Everything", oneliner = "the long one"),
            group("exact", "Summarize", oneliner = "the short one"),
        )
        assertEquals(
            listOf("exact", "longer"),
            promptIds("Summarize", groups),
        )
    }

    @Test
    fun emptyQueryShowsEverything() {
        val groups = listOf(group("1", "Alpha"), group("2", "Beta"))
        assertEquals(listOf("1", "2"), promptIds("", groups))
    }

    @Test
    fun nonMatchingQueryReturnsNothing() {
        val groups = listOf(group("1", "Alpha"))
        assertEquals(emptyList(), promptIds("zzzz", groups))
    }

    @Test
    fun emptyLibraryReturnsNothing() {
        assertEquals(emptyList(), promptIds("summ", emptyList()))
    }

    @Test
    fun resultsAreCappedSoTheListCannotSwallowTheScreen() {
        val groups = (1..30).map { group(it.toString(), "Prompt $it") }
        assertEquals(8, promptIds("prompt", groups).size)
    }

    // ── The combined `/` + `$` picker ───────────────────────────────────

    /** The picker where a skill can be picked. */
    private fun parsePickerQuery(text: String) = parsePickerQuery(text, canPickSkills = true)

    @Test
    fun eitherTriggerOpensThePicker() {
        assertEquals(PickerQuery(scope = null, query = "summ"), parsePickerQuery("/summ"))
        assertEquals(PickerQuery(PickerScope.SKILLS, "pdf", commandLength = 1), parsePickerQuery("\$pdf"))
        assertEquals(PickerQuery(PickerScope.SKILLS, "", commandLength = 1), parsePickerQuery("\$"))
    }

    @Test
    fun aDollarOpensNothingWhereNoSkillCanBePicked() {
        // A price or a ticker, in a chat without skills: no list over it.
        assertNull(parsePickerQuery("\$100", canPickSkills = false))
        assertNull(parsePickerQuery("\$AAPL", canPickSkills = false))
        // The slash still lists the prompts.
        assertEquals(PickerQuery(scope = null, query = "summ"), parsePickerQuery("/summ", canPickSkills = false))
    }

    @Test
    fun aSpaceClosesEitherTrigger() {
        assertNull(parsePickerQuery("/summ arize"))
        assertNull(parsePickerQuery("\$pdf tools"))
        assertNull(parsePickerQuery("\$ "))
    }

    @Test
    fun aTriggerMidTextDoesNotOpenThePicker() {
        // Prices and shell variables must not open the list mid-sentence.
        assertNull(parsePickerQuery("it costs \$5"))
        assertNull(parsePickerQuery("echo \$HOME"))
        assertNull(parsePickerQuery(" \$pdf"))
        assertNull(parsePickerQuery("what is 1/2"))
        assertNull(parsePickerQuery(""))
    }

    @Test
    fun aDollarListsSkillsOnly() {
        assertEquals(PickerScope.SKILLS, parsePickerQuery("\$pdf")?.scope)
    }

    private fun skill(name: String, title: String = name, description: String? = null) =
        PickerSkill(name = name, title = title, description = description)

    private fun List<ComposerPickerItem>.keys() = map { it.key }

    @Test
    fun slashListsPromptsThenSkills() {
        val rows = buildPickerRows(
            PickerQuery(scope = null, query = ""),
            prompts = listOf(group("p1", "Alpha"), group("p2", "Beta")),
            skills = listOf(skill("pdf-tools"), skill("brand-voice")),
        )
        assertEquals(listOf("prompt:p1", "prompt:p2", "skill:pdf-tools", "skill:brand-voice"), rows.keys())
    }

    @Test
    fun aScopeListsOnlyItsType() {
        val prompts = listOf(group("p1", "Alpha"))
        val skills = listOf(skill("pdf-tools"), skill("brand-voice"))
        assertEquals(
            listOf("skill:pdf-tools", "skill:brand-voice"),
            buildPickerRows(PickerQuery(PickerScope.SKILLS, ""), prompts, skills).keys(),
        )
        assertEquals(listOf("prompt:p1"), buildPickerRows(PickerQuery(PickerScope.PROMPTS, ""), prompts, skills).keys())
    }

    @Test
    fun slashKeepsTodaysPromptOrder() {
        // Skill rows never change the prompt rows `/` shows or their order.
        val prompts = listOf(
            group("longer", "Summarize Everything", oneliner = "the long one"),
            group("exact", "Summarize", oneliner = "the short one"),
        )
        val rows = buildPickerRows(
            PickerQuery(scope = null, query = "Summarize"),
            prompts,
            listOf(skill("summarize-pdf")),
        )
        assertEquals(
            promptIds("Summarize", prompts).map { "prompt:$it" },
            rows.filterIsInstance<ComposerPickerItem.Prompt>().keys(),
        )
        assertEquals(listOf("prompt:exact", "prompt:longer", "skill:summarize-pdf"), rows.keys())
    }

    @Test
    fun skillsMatchOnTitleDescriptionAndName() {
        val skills = listOf(
            skill("pdf-tools", title = "PDF tools"),
            skill("brand-voice", title = "Brand voice", description = "rewrite in our house style"),
            skill("unrelated"),
        )
        assertEquals(
            listOf("skill:pdf-tools"),
            buildPickerRows(PickerQuery(PickerScope.SKILLS, "pdf"), emptyList(), skills).keys(),
        )
        assertEquals(
            listOf("skill:brand-voice"),
            buildPickerRows(PickerQuery(PickerScope.SKILLS, "house style"), emptyList(), skills).keys(),
        )
    }

    @Test
    fun theMixedListIsCappedPromptsFirstAndAScopeIsNot() {
        val prompts = (1..10).map { group("p$it", "Item prompt $it") }
        val skills = (1..10).map { skill("item-skill-$it") }
        val mixed = buildPickerRows(PickerQuery(scope = null, query = "item"), prompts, skills)
        assertEquals(8, mixed.size)
        assertEquals(true, mixed.all { it is ComposerPickerItem.Prompt })
        assertEquals(10, buildPickerRows(PickerQuery(PickerScope.SKILLS, "item"), prompts, skills).size)
        assertEquals(10, buildPickerRows(PickerQuery(PickerScope.PROMPTS, "item"), prompts, skills).size)
    }

    @Test
    fun noMatchesInEitherTypeIsAnEmptyList() {
        val rows = buildPickerRows(
            PickerQuery(PickerScope.SKILLS, "zzzz"),
            listOf(group("1", "Alpha")),
            listOf(skill("pdf")),
        )
        assertEquals(emptyList(), rows)
    }

    // ── Scopes: `/prompt `, `/skill ` ────────────────────────────────────

    @Test
    fun aScopeCommandAndASpaceListOneType() {
        assertEquals(PickerQuery(PickerScope.SKILLS, "", commandLength = 6), parsePickerQuery("/skill "))
        assertEquals(PickerQuery(PickerScope.PROMPTS, "summ", commandLength = 7), parsePickerQuery("/prompt summ"))
    }

    @Test
    fun scopeCommandsTakeThePluralAndAnyCase() {
        assertEquals(PickerQuery(PickerScope.SKILLS, "sp", commandLength = 7), parsePickerQuery("/Skills sp"))
        assertEquals(PickerScope.PROMPTS, parsePickerQuery("/PROMPTS ")?.scope)
    }

    @Test
    fun aScopedQueryKeepsItsSpaces() {
        assertEquals("code rev", parsePickerQuery("/prompt code rev")?.query)
    }

    @Test
    fun aCommandWithoutItsSpaceIsAnOrdinaryQuery() {
        assertEquals(PickerQuery(scope = null, query = "skill"), parsePickerQuery("/skill"))
    }

    @Test
    fun anUnknownCommandWithASpaceClosesThePicker() {
        assertNull(parsePickerQuery("/summ arize"))
        assertNull(parsePickerQuery("/skillz pdf"))
    }

    @Test
    fun theSkillScopeOpensWhereNoSkillCanBePicked() {
        assertEquals(PickerScope.SKILLS, parsePickerQuery("/skill ", canPickSkills = false)?.scope)
    }

    @Test
    fun theCommandRangeCoversTheScopeCommandOnly() {
        assertEquals(0 until 6, pickerCommandRange("/skill pdf", canPickSkills = true))
        assertEquals(0 until 7, pickerCommandRange("/Prompt code", canPickSkills = true))
        assertEquals(0 until 1, pickerCommandRange("\$pdf", canPickSkills = true))
        assertNull(pickerCommandRange("\$5", canPickSkills = false))
        assertNull(pickerCommandRange("/summ", canPickSkills = true))
    }

    @Test
    fun onlyAPickerThatListsSkillsNeedsTheCatalog() {
        assertEquals(true, pickerListsSkills("/", canPickSkills = true))
        assertEquals(true, pickerListsSkills("/skill ", canPickSkills = true))
        assertEquals(true, pickerListsSkills("\$", canPickSkills = true))
        assertEquals(false, pickerListsSkills("/prompt ", canPickSkills = true))
        assertEquals(false, pickerListsSkills("/mcp ", canPickSkills = true))
        assertEquals(false, pickerListsSkills("/skill ", canPickSkills = false))
        assertEquals(false, pickerListsSkills("hello", canPickSkills = true))
    }

    @Test
    fun aTrailingSpaceInAScopedQueryStillMatches() {
        val groups = listOf(group("1", "Code review", oneliner = "bugs and style"))
        assertEquals(
            listOf("prompt:1"),
            buildPickerRows(PickerQuery(PickerScope.PROMPTS, "code review "), groups, emptyList()).keys(),
        )
    }

    // ── MCP servers: `/mcp ` ─────────────────────────────────────────────

    private fun server(name: String, title: String? = null, description: String? = null) = PickerMcpServer(
        server = McpServerDisplayData(name, title, description, McpDisplayStatus.CONNECTED),
        on = false,
        locked = false,
    )

    @Test
    fun theMcpCommandListsServersAndIsDrawnAsAToken() {
        assertEquals(PickerQuery(PickerScope.MCP, "git hub", commandLength = 4), parsePickerQuery("/MCP git hub"))
        assertEquals(PickerScope.MCP, parsePickerQuery("/mcp ", canPickSkills = false)?.scope)
        assertEquals(0 until 4, pickerCommandRange("/mcp gi", canPickSkills = false))
        assertNull(parsePickerQuery("/mcps "))
    }

    @Test
    fun serversCloseTheMixedListAfterPromptsAndSkills() {
        val rows = buildPickerRows(
            PickerQuery(scope = null, query = "git"),
            prompts = listOf(group("p1", "Git commit message")),
            skills = listOf(skill("git-flow")),
            mcpServers = listOf(server("github", title = "GitHub"), server("linear")),
        )
        assertEquals(listOf("prompt:p1", "skill:git-flow", "mcp:github"), rows.keys())
    }

    @Test
    fun theMcpScopeMatchesTitleDescriptionAndNameAndListsNothingElse() {
        val servers = listOf(
            server("gh", title = "GitHub"),
            server("tracker", title = "Linear", description = "issues and projects"),
            server("files-srv"),
        )
        fun keys(query: String) =
            buildPickerRows(PickerQuery(PickerScope.MCP, query), listOf(group("p", "GitHub prompt")), emptyList(), servers).keys()
        assertEquals(listOf("mcp:gh"), keys("github"))
        assertEquals(listOf("mcp:tracker"), keys("issues "))
        assertEquals(listOf("mcp:files-srv"), keys("files"))
        assertEquals(3, keys("").size)
    }

    private fun model(name: String, group: String?) = PickerModel(
        key = "model:$group::$name",
        title = name,
        description = null,
        group = group?.let { HeaderLabel.Text(it) },
        icon = null,
        selected = false,
        starred = false,
        keyHint = null,
        pick = ModelPick.Raw(group.orEmpty(), name),
    )

    @Test
    fun theModelCommandOpensItsScopeAndIsDrawnAsAToken() {
        assertEquals(PickerQuery(PickerScope.MODEL, "gpt 4", commandLength = 6), parsePickerQuery("/Model gpt 4"))
        assertEquals(0 until 6, pickerCommandRange("/model ", canPickSkills = false))
        assertNull(parsePickerQuery("/models "))
    }

    @Test
    fun modelsAreListedOnlyUnderTheModelCommand() {
        val models = listOf(model("gpt-4o", "OpenAI"))
        val prompts = listOf(group("p1", "gpt helper"))
        val mixed = buildPickerRows(PickerQuery(scope = null, query = "gpt"), prompts, emptyList(), models = models)
        assertEquals(listOf("prompt:p1"), mixed.keys())
        val scoped = buildPickerRows(PickerQuery(PickerScope.MODEL, "gpt"), prompts, emptyList(), models = models)
        assertEquals(listOf("model:model:OpenAI::gpt-4o"), scoped.keys())
    }

    @Test
    fun theModelScopeMatchesTheModelBeforeItsSection() {
        val models = listOf(
            model("claude-sonnet", "Anthropic"),
            model("open-mixtral", "Mistral"),
            model("gpt-4o", "OpenAI"),
        )
        fun titles(query: String) =
            buildPickerRows(PickerQuery(PickerScope.MODEL, query), emptyList(), emptyList(), models = models)
                .map { (it as ComposerPickerItem.Model).model.title }
        assertEquals(listOf("open-mixtral", "gpt-4o"), titles("open "))
        assertEquals(listOf("claude-sonnet"), titles("anthropic"))
        assertEquals(3, titles("").size)
    }

    @Test
    fun anEndpointWaitingForItsKeyIsFoundByTheModelsItHides() {
        val keyless = model("OpenAI", group = null).copy(
            pick = ModelPick.AddKey("openAI"),
            hiddenModels = listOf("gpt-4o", "o3"),
        )
        val models = listOf(model("gpt-oss", "Groq"), keyless)
        fun titles(query: String) =
            buildPickerRows(PickerQuery(PickerScope.MODEL, query), emptyList(), emptyList(), models = models)
                .map { (it as ComposerPickerItem.Model).model.title }
        assertEquals(listOf("gpt-oss", "OpenAI"), titles("gpt"))
        assertEquals(listOf("OpenAI"), titles("o3"))
    }
}
