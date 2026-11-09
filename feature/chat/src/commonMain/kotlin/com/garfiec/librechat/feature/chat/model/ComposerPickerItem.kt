package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.chat.util.PickerModel

/**
 * A skill as a row of the composer's picker.
 *
 * [name] is what a pick stages and what the turn sends (`manualSkills` carries names, not ids);
 * [title] is what the row shows (`displayTitle ?: name`).
 */
@Immutable
data class PickerSkill(
    val name: String,
    val title: String,
    val description: String?,
)

/** What a tap on an MCP server's picker row does: the "+" sheet's MCP row rule. */
enum class McpPick { TOGGLE, SIGN_IN, SET_UP, NONE }

/**
 * An MCP server as a row of the composer's picker: [on] for this chat, [locked] on by the active
 * spec. A server that is on always switches off, so nothing keeps an unusable server in the chat;
 * sign-in and set-up outrank a lock.
 */
@Immutable
data class PickerMcpServer(
    val server: McpServerDisplayData,
    val on: Boolean,
    val locked: Boolean,
) {
    val pick: McpPick
        get() = when {
            on && !locked -> McpPick.TOGGLE
            server.setsUpOnTap -> McpPick.SET_UP
            server.signsInOnTap -> if (server.signInBusy) McpPick.NONE else McpPick.SIGN_IN
            locked || server.signInPending -> McpPick.NONE
            else -> McpPick.TOGGLE
        }
}

/**
 * The chat's MCP servers as picker rows, or none where the "+" sheet offers no MCP section: an
 * agent or a `hideBadgeRow` spec ([toolbarShown]) or a role without MCP ([mcpAllowed]).
 */
fun pickerMcpServersOf(
    servers: List<McpServerDisplayData>,
    on: Set<String>,
    locked: Set<String>,
    toolbarShown: Boolean,
    mcpAllowed: Boolean,
): List<PickerMcpServer> =
    if (!toolbarShown || !mcpAllowed) {
        emptyList()
    } else {
        servers.map { PickerMcpServer(server = it, on = it.name in on, locked = it.name in locked) }
    }

/**
 * One row of the composer's picker.
 *
 * Web keeps `$` (skills) apart and reaches MCP servers from its tools palette. Here `/` lists
 * prompts, skills and MCP servers, each row saying which it is; `/prompt `, `/skill `, `$`, `/mcp `
 * and `/model ` list one type.
 */
@Immutable
sealed interface ComposerPickerItem {
    /** Stable across types: a prompt group's id, a skill's name and a server's name could otherwise collide. */
    val key: String

    @Immutable
    data class Prompt(val prompt: PromptMentionDisplayData) : ComposerPickerItem {
        override val key: String get() = "prompt:${prompt.id}"
    }

    @Immutable
    data class Skill(val skill: PickerSkill) : ComposerPickerItem {
        override val key: String get() = "skill:${skill.name}"
    }

    /** A pick switches the server for this chat, or starts what it needs first ([PickerMcpServer.pick]). */
    @Immutable
    data class Mcp(val mcp: PickerMcpServer) : ComposerPickerItem {
        override val key: String get() = "mcp:${mcp.server.name}"
    }

    /** A pick switches the chat's model as the model sheet does ([PickerModel.pick]). */
    @Immutable
    data class Model(val model: PickerModel) : ComposerPickerItem {
        override val key: String get() = "model:${model.key}"
    }
}
