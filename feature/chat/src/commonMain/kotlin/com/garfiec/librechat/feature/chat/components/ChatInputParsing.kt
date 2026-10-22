package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData

/** The character that opened the composer's picker. It decides which type is listed first. */
enum class PickerTrigger(val char: Char) {
    /** Prompts first, then skills. */
    SLASH('/'),

    /** Skills first, then prompts. */
    DOLLAR('$'),
}

/** An open picker: what opened it and the text typed after it. */
data class PickerQuery(val trigger: PickerTrigger, val query: String)

/**
 * The open picker for the composer text, or null when it should be closed.
 *
 * Either trigger opens the same list of prompts and skills. The trigger must lead the input, so a
 * date, path or fraction (`1/2`) and a price mid-sentence (`$5`) never open it, and a space closes
 * the list, which is how the user dismisses it without clearing what they typed. There is no
 * setting to turn `$` off.
 *
 * `$` opens the list only where a skill could be picked ([canPickSkills], which is
 * `ChatUiState.canPickSkills`, the persisted agent's skill scope included): elsewhere it is just
 * the start of a price or a ticker, and `/` still lists the prompts.
 */
fun parsePickerQuery(text: String, canPickSkills: Boolean): PickerQuery? {
    val first = text.firstOrNull() ?: return null
    val trigger = PickerTrigger.entries.firstOrNull { it.char == first } ?: return null
    if (trigger == PickerTrigger.DOLLAR && !canPickSkills) return null
    val query = text.substring(1)
    return if (query.contains(' ')) null else PickerQuery(trigger, query)
}

/**
 * The label the query is matched against, mirroring the web client's composed option label so both
 * clients rank the same prompt the same way. The body stands in for a missing oneliner, which is
 * what lets a query match a prompt whose title never mentions it.
 */
private fun PromptMentionDisplayData.searchLabel(): String {
    val description = oneliner?.takeIf { it.isNotBlank() } ?: promptText.orEmpty()
    return if (description.isBlank()) name else "$name: $description"
}

/** Rows the picker shows at most, prompts and skills together. */
internal const val MAX_SUGGESTIONS = 8

/**
 * Ranks [prompts] against a picker [query], best first.
 *
 * Matching deliberately does not require a `command`: the server's list projection omits that field
 * entirely, so filtering on it yields an empty picker no matter what the user's library contains.
 *
 * The second key falls back to the name (web's `command ?? name`) rather than matching on `command`
 * alone. Since `command` is always absent, a `command`-only key would never rank anything, and the
 * label key tops out at `STARTS_WITH` because the name is a prefix of `"name: description"` — so
 * typing a prompt's exact name would not float it above a prompt that merely starts with it.
 */
private fun rankPrompts(query: String, prompts: List<PromptMentionDisplayData>): List<PromptMentionDisplayData> =
    matchSorter(
        items = prompts,
        query = query,
        keys = listOf({ it.searchLabel() }, { it.command ?: it.name }),
    )

/** The same composed label shape as a prompt's, so a description can match the query too. */
private fun PickerSkill.searchLabel(): String =
    if (description.isNullOrBlank()) title else "$title: $description"

private fun rankSkills(query: String, skills: List<PickerSkill>): List<PickerSkill> =
    matchSorter(
        items = skills,
        query = query,
        keys = listOf({ it.searchLabel() }, { it.name }),
    )

/**
 * The rows the open [picker] shows: [prompts] and [skills] each ranked against the query, the
 * trigger's own type first (`/` lists prompts then skills; `$` lists skills then prompts), capped
 * at [MAX_SUGGESTIONS] together.
 *
 * [skills] arrive already narrowed to what may be picked here (see `pickableSkills`).
 */
fun buildPickerRows(
    picker: PickerQuery,
    prompts: List<PromptMentionDisplayData>,
    skills: List<PickerSkill>,
): List<ComposerPickerItem> {
    val promptRows = rankPrompts(picker.query, prompts).map { ComposerPickerItem.Prompt(it) }
    val skillRows = rankSkills(picker.query, skills).map { ComposerPickerItem.Skill(it) }
    val ordered = when (picker.trigger) {
        PickerTrigger.SLASH -> promptRows + skillRows
        PickerTrigger.DOLLAR -> skillRows + promptRows
    }
    return ordered.take(MAX_SUGGESTIONS)
}
