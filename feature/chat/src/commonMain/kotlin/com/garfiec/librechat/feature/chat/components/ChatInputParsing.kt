package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PickerMcpServer
import com.garfiec.librechat.feature.chat.model.PickerSkill
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.util.HeaderLabel
import com.garfiec.librechat.feature.chat.util.PickerModel

/** The one type a scoped picker lists. */
enum class PickerScope { PROMPTS, SKILLS, MCP, MODEL }

/**
 * An open picker: the [scope] it lists (null = every type together) and the [query] typed after its
 * command. The first [commandLength] characters of the text are the command the field draws as a
 * token (`/skill`, `/prompt`, `/mcp`, `/model`, `$`); 0 for the plain `/`.
 */
data class PickerQuery(val scope: PickerScope?, val query: String, val commandLength: Int = 0)

private val SCOPE_COMMANDS = mapOf(
    "prompt" to PickerScope.PROMPTS,
    "prompts" to PickerScope.PROMPTS,
    "skill" to PickerScope.SKILLS,
    "skills" to PickerScope.SKILLS,
    "mcp" to PickerScope.MCP,
    "model" to PickerScope.MODEL,
)

/**
 * The open picker for the composer text, or null when it should be closed.
 *
 * The trigger must lead the input, so a date, path or fraction (`1/2`) and a price mid-sentence
 * (`$5`) never open it.
 * - `/` lists prompts, skills and MCP servers together. A space closes it, which is how the user
 *   dismisses it without clearing what they typed.
 * - `/prompt `, `/skill ` (either plural or any case), `/mcp ` and `/model ` list one type, and their
 *   query may hold spaces. `/skill `, `/mcp ` and `/model ` open even where none can be picked, so the
 *   list can say so.
 * - `$` lists skills, only where a skill could be picked ([canPickSkills], which is
 *   `ChatUiState.canPickSkills`, the persisted agent's skill scope included): elsewhere it is just
 *   the start of a price or a ticker. A space closes it, so a price still types freely.
 */
fun parsePickerQuery(text: String, canPickSkills: Boolean): PickerQuery? = when (text.firstOrNull()) {
    '/' -> {
        val space = text.indexOf(' ')
        if (space < 0) {
            PickerQuery(scope = null, query = text.substring(1))
        } else {
            SCOPE_COMMANDS[text.substring(1, space).lowercase()]?.let { scope ->
                PickerQuery(scope = scope, query = text.substring(space + 1), commandLength = space)
            }
        }
    }
    '$' -> text.substring(1).takeIf { canPickSkills && ' ' !in it }?.let {
        PickerQuery(scope = PickerScope.SKILLS, query = it, commandLength = 1)
    }
    else -> null
}

/** Whether the picker [text] opens lists skills, which is when the skill catalog is needed. */
fun pickerListsSkills(text: String, canPickSkills: Boolean): Boolean =
    canPickSkills &&
        parsePickerQuery(text, canPickSkills)?.let { it.scope == null || it.scope == PickerScope.SKILLS } == true

/** The command a scoped picker's text starts with, which the field draws as a token. */
fun pickerCommandRange(text: String, canPickSkills: Boolean): IntRange? =
    parsePickerQuery(text, canPickSkills)?.takeIf { it.commandLength > 0 }?.let { 0 until it.commandLength }

/**
 * The label the query is matched against, mirroring the web client's composed option label so both
 * clients rank the same prompt the same way. The body stands in for a missing oneliner, which is
 * what lets a query match a prompt whose title never mentions it.
 */
private fun PromptMentionDisplayData.searchLabel(): String {
    val description = oneliner?.takeIf { it.isNotBlank() } ?: promptText.orEmpty()
    return if (description.isBlank()) name else "$name: $description"
}

/** Rows the mixed `/` list shows at most. A scoped list shows every match. */
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

private fun PickerMcpServer.searchLabel(): String =
    server.description?.takeIf { it.isNotBlank() }?.let { "${server.label}: $it" } ?: server.label

private fun rankMcpServers(query: String, servers: List<PickerMcpServer>): List<PickerMcpServer> =
    matchSorter(
        items = servers,
        query = query,
        keys = listOf({ it.searchLabel() }, { it.server.name }),
    )

/**
 * A model's section counts too, so `/model openrouter` lists that endpoint's models, and an endpoint
 * waiting for its key is found by the models it hides.
 */
private fun rankModels(query: String, models: List<PickerModel>): List<PickerModel> =
    matchSorter(
        items = models,
        query = query,
        keys = listOf(
            { it.title },
            { (it.group as? HeaderLabel.Text)?.text },
            { model -> model.hiddenModels.firstOrNull { it.contains(query, ignoreCase = true) } },
        ),
    )

/**
 * The rows the open [picker] shows, each type ranked against the query. The mixed list holds
 * prompts, then skills, then MCP servers, capped at [MAX_SUGGESTIONS]; a scoped list holds every
 * match of its type.
 *
 * [skills] arrive already narrowed to what may be picked here (see `pickableSkills`), and
 * [mcpServers] and [models] are empty where none can be picked. [models] only ever fill `/model `.
 */
fun buildPickerRows(
    picker: PickerQuery,
    prompts: List<PromptMentionDisplayData>,
    skills: List<PickerSkill>,
    mcpServers: List<PickerMcpServer> = emptyList(),
    models: List<PickerModel> = emptyList(),
): List<ComposerPickerItem> {
    // A scoped query may end in the space before the next word, which no label would contain.
    val query = picker.query.trim()
    fun promptRows() = rankPrompts(query, prompts).map { ComposerPickerItem.Prompt(it) }
    fun skillRows() = rankSkills(query, skills).map { ComposerPickerItem.Skill(it) }
    fun mcpRows() = rankMcpServers(query, mcpServers).map { ComposerPickerItem.Mcp(it) }
    return when (picker.scope) {
        null -> (promptRows() + skillRows() + mcpRows()).take(MAX_SUGGESTIONS)
        PickerScope.PROMPTS -> promptRows()
        PickerScope.SKILLS -> skillRows()
        PickerScope.MCP -> mcpRows()
        PickerScope.MODEL -> rankModels(query, models).map { ComposerPickerItem.Model(it) }
    }
}
