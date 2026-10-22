package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
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
        return withSkills?.takeIf { it.trigger == PickerTrigger.SLASH }?.query
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
        buildPickerRows(PickerQuery(PickerTrigger.SLASH, query), prompts, emptyList())
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
        assertEquals(PickerQuery(PickerTrigger.SLASH, "summ"), parsePickerQuery("/summ"))
        assertEquals(PickerQuery(PickerTrigger.DOLLAR, "pdf"), parsePickerQuery("\$pdf"))
        assertEquals(PickerQuery(PickerTrigger.DOLLAR, ""), parsePickerQuery("\$"))
    }

    @Test
    fun aDollarOpensNothingWhereNoSkillCanBePicked() {
        // A price or a ticker, in a chat without skills: no list over it.
        assertNull(parsePickerQuery("\$100", canPickSkills = false))
        assertNull(parsePickerQuery("\$AAPL", canPickSkills = false))
        // The slash still lists the prompts.
        assertEquals(PickerQuery(PickerTrigger.SLASH, "summ"), parsePickerQuery("/summ", canPickSkills = false))
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
    fun aDollarIsNeverReadAsASlashQuery() {
        assertEquals(PickerTrigger.DOLLAR, parsePickerQuery("\$pdf")?.trigger)
    }

    private fun skill(name: String, title: String = name, description: String? = null) =
        PickerSkill(name = name, title = title, description = description)

    private fun List<ComposerPickerItem>.keys() = map { it.key }

    @Test
    fun slashListsPromptsThenSkills() {
        val rows = buildPickerRows(
            PickerQuery(PickerTrigger.SLASH, ""),
            prompts = listOf(group("p1", "Alpha"), group("p2", "Beta")),
            skills = listOf(skill("pdf-tools"), skill("brand-voice")),
        )
        assertEquals(listOf("prompt:p1", "prompt:p2", "skill:pdf-tools", "skill:brand-voice"), rows.keys())
    }

    @Test
    fun dollarListsSkillsThenPrompts() {
        val rows = buildPickerRows(
            PickerQuery(PickerTrigger.DOLLAR, ""),
            prompts = listOf(group("p1", "Alpha")),
            skills = listOf(skill("pdf-tools"), skill("brand-voice")),
        )
        assertEquals(listOf("skill:pdf-tools", "skill:brand-voice", "prompt:p1"), rows.keys())
    }

    @Test
    fun slashKeepsTodaysPromptOrder() {
        // Skill rows never change the prompt rows `/` shows or their order.
        val prompts = listOf(
            group("longer", "Summarize Everything", oneliner = "the long one"),
            group("exact", "Summarize", oneliner = "the short one"),
        )
        val rows = buildPickerRows(
            PickerQuery(PickerTrigger.SLASH, "Summarize"),
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
            buildPickerRows(PickerQuery(PickerTrigger.DOLLAR, "pdf"), emptyList(), skills).keys(),
        )
        assertEquals(
            listOf("skill:brand-voice"),
            buildPickerRows(PickerQuery(PickerTrigger.DOLLAR, "house style"), emptyList(), skills).keys(),
        )
    }

    @Test
    fun theTriggersTypeFillsTheCapFirst() {
        val prompts = (1..10).map { group("p$it", "Item prompt $it") }
        val skills = (1..10).map { skill("item-skill-$it") }
        val slash = buildPickerRows(PickerQuery(PickerTrigger.SLASH, "item"), prompts, skills)
        val dollar = buildPickerRows(PickerQuery(PickerTrigger.DOLLAR, "item"), prompts, skills)
        assertEquals(8, slash.size)
        assertEquals(8, dollar.size)
        assertEquals(true, slash.all { it is ComposerPickerItem.Prompt })
        assertEquals(true, dollar.all { it is ComposerPickerItem.Skill })
    }

    @Test
    fun noMatchesInEitherTypeIsAnEmptyList() {
        val rows = buildPickerRows(
            PickerQuery(PickerTrigger.DOLLAR, "zzzz"),
            listOf(group("1", "Alpha")),
            listOf(skill("pdf")),
        )
        assertEquals(emptyList(), rows)
    }
}
