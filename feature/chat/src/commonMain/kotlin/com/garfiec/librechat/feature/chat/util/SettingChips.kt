package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData

/**
 * How a chip on the composer's settings line reads. [ON] is filled; [PINNED_OFF] is a server-pinned
 * tool (`interface.defaultPinnedTools`) that is off, drawn outlined so it is one tap away; [LOCKED]
 * is on because the active model spec turns it on, filled with a lock.
 */
enum class SettingChipState { ON, PINNED_OFF, LOCKED }

@Immutable
sealed interface SettingChip {
    /** Null for a chip that is a button rather than a switch (Thinking). */
    val state: SettingChipState?

    /** A tool from the "+" sheet; [pinned] when the server pins it, so turning it off keeps it on the line. */
    data class Tool(val key: String, override val state: SettingChipState, val pinned: Boolean) : SettingChip

    data class Mcp(val server: McpServerDisplayData, override val state: SettingChipState) : SettingChip {
        val name: String get() = server.name
        val label: String get() = server.label
        val status: McpDisplayStatus get() = server.status

        /** [McpServerDisplayData.signsInOnTap], which the "+" sheet's MCP section counts too. */
        val signsInOnTap: Boolean get() = server.signsInOnTap

        val signInPending: Boolean get() = server.signInPending

        /** A reconnect or cancel is out ([signInBusy]) and would ignore this chip's tap, so it is disabled. */
        val waitsForSignIn: Boolean
            get() = server.signInBusy && (signInPending || signsInOnTap)

        val opensMenu: Boolean
            get() = signInPending && !waitsForSignIn
    }

    data class Thinking(val chip: ThinkingChip) : SettingChip {
        override val state: SettingChipState? get() = null
    }
}

@Immutable
data class SettingChips(
    val chips: List<SettingChip> = emptyList(),
    /** The label of the spec that locks the [SettingChipState.LOCKED] chips, for "Set by …". */
    val lockedBy: String? = null,
) {
    val isEmpty: Boolean get() = chips.isEmpty()

    companion object {
        val EMPTY = SettingChips()
    }
}

/** [openTools] are the tools whose gate is open; a tool outside it never shows, whatever its stored toggle. */
@Immutable
data class SettingChipInputs(
    /** The composer shows its tool controls (`toolbarShown`): false for an agent or a `hideBadgeRow` spec. */
    val toolbarShown: Boolean,
    /** The tools in use (`effectiveEnabledTools`). */
    val toolsOn: Set<String>,
    val openTools: Set<String>,
    /** The server's `defaultPinnedTools`, raw. */
    val pinned: List<String>,
    val lockedTools: Set<String>,
    val mcpServers: List<McpServerDisplayData>,
    /** The MCP servers in use (`effectiveMcpServerNames`). */
    val mcpOn: Set<String>,
    val lockedMcpServers: Set<String>,
    /** The role may use MCP servers; a spec's own servers show regardless. */
    val mcpAllowed: Boolean,
    val specLabel: String?,
    val thinking: ThinkingChip?,
)

/** Without the tool controls (an agent, `hideBadgeRow`) only Thinking is left; the request still carries the spec's tools. */
fun settingChipsOf(inputs: SettingChipInputs): SettingChips {
    val chips = buildList {
        if (inputs.toolbarShown) {
            val pinned = inputs.pinned.mapNotNullTo(mutableSetOf(), ::pinnedToolKey)
            SETTING_TOOL_ORDER.forEach { key ->
                if (key !in inputs.openTools) return@forEach
                val isPinned = key in pinned
                val state = when {
                    key in inputs.lockedTools -> SettingChipState.LOCKED
                    key in inputs.toolsOn -> SettingChipState.ON
                    isPinned -> SettingChipState.PINNED_OFF
                    else -> return@forEach
                }
                add(SettingChip.Tool(key, state, isPinned))
            }
            inputs.mcpServers.forEach { server ->
                if (server.name !in inputs.mcpOn) return@forEach
                val locked = server.name in inputs.lockedMcpServers
                if (!locked && !inputs.mcpAllowed) return@forEach
                add(SettingChip.Mcp(server, if (locked) SettingChipState.LOCKED else SettingChipState.ON))
            }
        }
        inputs.thinking?.let { add(SettingChip.Thinking(it)) }
    }
    if (chips.isEmpty()) return SettingChips.EMPTY
    val anyLocked = chips.any { it.state == SettingChipState.LOCKED }
    return SettingChips(chips, lockedBy = inputs.specLabel?.takeIf { anyLocked && it.isNotBlank() })
}

/**
 * Gathers the inputs only when a key (`ChatUiState.settingChipSlices`) is a new instance, and keeps its
 * last result for equal inputs, so a streaming flush costs nothing. Not thread-safe: one collector only.
 */
internal class SettingChipsMemo {
    private var keys: List<Any?>? = null
    private var inputs: SettingChipInputs? = null
    private var result: SettingChips = SettingChips.EMPTY

    fun resolve(keys: List<Any?>, inputs: () -> SettingChipInputs): SettingChips {
        val last = this.keys
        if (last != null && last.size == keys.size && last.indices.all { last[it] === keys[it] }) return result
        this.keys = keys
        val next = inputs()
        if (next != this.inputs) {
            result = settingChipsOf(next)
            this.inputs = next
        }
        return result
    }
}

/** A pinned key as mobile names the tool (`execute_code` is the code interpreter), or null for one it has no toggle for. */
fun pinnedToolKey(key: String): String? = when (key) {
    ToolConstants.EXECUTE_CODE, ToolConstants.CODE_INTERPRETER -> ToolConstants.CODE_INTERPRETER
    in SETTING_TOOL_ORDER -> key
    else -> null
}

/** The tools the "+" sheet offers rows for and the settings line can show, in that order. */
val SETTING_TOOL_ORDER: List<String> = listOf(
    ToolConstants.WEB_SEARCH,
    ToolConstants.URL_CONTEXT,
    ToolConstants.CODE_INTERPRETER,
    ToolConstants.FILE_SEARCH,
    ToolConstants.MEMORY,
    ToolConstants.SKILLS,
    ToolConstants.ARTIFACTS,
)

/** The row the "+" sheet opens on, scrolled into view and marked, for a settings chip's tap. */
sealed interface ToolsSheetFocus {
    data class Tool(val key: String) : ToolsSheetFocus

    data class McpServer(val name: String) : ToolsSheetFocus
}

sealed interface SettingChipTap {
    /** A spec's own tool: say who set it ([specLabel], null when the spec has no label to name). */
    data class ExplainLock(val specLabel: String?) : SettingChipTap

    /** A pinned tool that is off: switch it on (harmless, so one tap). */
    data object TurnOn : SettingChipTap

    /** Anything that is on: open the "+" sheet at its row. A tap on the line never turns a thing off. */
    data class ShowInSheet(val focus: ToolsSheetFocus) : SettingChipTap

    data object OpenThinking : SettingChipTap

    /** An MCP server that needs the user to sign in: start it ([serverName] is its stored name). */
    data class SignIn(val serverName: String) : SettingChipTap

    /** The chip opens its own sign-in menu; the screen ignores the tap. */
    data object SignInMenu : SettingChipTap

    /** An MCP chip whose sign-in can't start yet ([SettingChip.Mcp.waitsForSignIn]): nothing. */
    data object WaitForSignIn : SettingChipTap
}

/** Sign-in outranks a spec lock: the lock only says why the server is there, signing in makes it work. */
fun SettingChips.tapFor(chip: SettingChip): SettingChipTap {
    val state = chip.state ?: return SettingChipTap.OpenThinking
    return when {
        chip is SettingChip.Mcp && chip.waitsForSignIn -> SettingChipTap.WaitForSignIn
        chip is SettingChip.Mcp && chip.opensMenu -> SettingChipTap.SignInMenu
        chip is SettingChip.Mcp && chip.signsInOnTap -> SettingChipTap.SignIn(chip.name)
        state == SettingChipState.LOCKED -> SettingChipTap.ExplainLock(lockedBy)
        state == SettingChipState.PINNED_OFF -> SettingChipTap.TurnOn
        chip is SettingChip.Tool -> SettingChipTap.ShowInSheet(ToolsSheetFocus.Tool(chip.key))
        chip is SettingChip.Mcp -> SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer(chip.name))
        else -> SettingChipTap.OpenThinking
    }
}
