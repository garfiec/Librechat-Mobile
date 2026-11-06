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

/** One chip on the composer's settings line. */
@Immutable
sealed interface SettingChip {
    /** Null for a chip that is a button rather than a switch (Thinking). */
    val state: SettingChipState?

    /** A tool from the "+" sheet; [pinned] when the server pins it, so turning it off keeps it on the line. */
    data class Tool(val key: String, override val state: SettingChipState, val pinned: Boolean) : SettingChip

    /** A selected MCP server, with its last reported status. */
    data class Mcp(val server: McpServerDisplayData, override val state: SettingChipState) : SettingChip {
        val name: String get() = server.name
        val label: String get() = server.label
        val status: McpDisplayStatus get() = server.status

        /** It signs in with OAuth ([McpServerDisplayData.requiresOAuth]). */
        private val requiresOAuth: Boolean get() = server.requiresOAuth

        /**
         * A tap starts the sign-in rather than turning it off, and the chip says so ("· sign in", a
         * "Sign in" click label): a server that needs sign-in, or an OAuth server whose connection
         * failed. Signing in again is what fixes either, as web's MCP palette reconnects them; the
         * "+" sheet's switch still turns it off.
         */
        val signsInOnTap: Boolean
            get() = status == McpDisplayStatus.NEEDS_SIGN_IN || (requiresOAuth && status == McpDisplayStatus.FAILED)

        /**
         * An OAuth server that reads connecting: a sign-in is pending, maybe still being finished in
         * the browser, maybe abandoned. The chip says so ("· connecting") and its tap offers "Cancel
         * sign-in" and "Start over" instead of turning it off. The app never cancels the flow itself.
         */
        val signInPending: Boolean
            get() = requiresOAuth && status == McpDisplayStatus.CONNECTING

        /**
         * Its tap would sign in or open the sign-in menu, but a reconnect or a cancel is still out
         * ([signInBusy]), which would ignore either: the chip waits, disabled, until that ends.
         */
        val waitsForSignIn: Boolean
            get() = server.signInBusy && (signInPending || signsInOnTap)

        /** Its tap opens the "Cancel sign-in" / "Start over" menu ([SettingChipTap.SignInMenu]). */
        val opensMenu: Boolean
            get() = signInPending && !waitsForSignIn
    }

    /** The per-turn Thinking control; a tap opens its sheet. */
    data class Thinking(val chip: ThinkingChip) : SettingChip {
        override val state: SettingChipState? get() = null
    }
}

/** What the settings line shows: tools, then MCP servers, then Thinking. */
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

/**
 * What [settingChipsOf] reads, gathered from the chat state ([ChatUiState.settingChipInputs]).
 * [openTools] are the tools whose own gate is open, the same gates the "+" sheet offers its rows on;
 * a tool outside it never shows, even when a stored toggle says it is on.
 */
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

/**
 * The settings line, one chip per thing and nothing twice: the tools in the "+" sheet's order (each
 * on, pinned, or locked by the spec, and only where its gate is open), the selected MCP servers in
 * list order, then Thinking. Without the tool controls (an agent, `hideBadgeRow`) only Thinking is
 * left, as the request still carries the spec's own tools then.
 */
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
 * [settingChipsOf] that returns its last result while the inputs are equal, so the line keeps one
 * instance across the streaming reply's emissions. The inputs are gathered only when one of the
 * state's [keys] (`ChatUiState.settingChipSlices`, the slices they are read from) is not the same
 * instance as last time, so a streaming flush, which replaces none of them, gathers nothing. One
 * collector only.
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

/** A pinned key as mobile names the tool, or null for one it has no toggle for (`artifacts`, …). */
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
)

/**
 * Where a settings chip was turned off: the conversation and, in it, the queued item being edited
 * (null: the new-message draft). Its Undo applies only while both are still the composer's.
 */
data class SettingChipUndoScope(val conversationId: String?, val editingQueuedId: String?)

/** What a tap on a settings chip does. */
sealed interface SettingChipTap {
    /** A spec's own tool: say who set it ([specLabel], null when the spec has no label to name). */
    data class ExplainLock(val specLabel: String?) : SettingChipTap

    /** Switch it, and the chip stays (a pinned tool). */
    data object Toggle : SettingChipTap

    /** Switch it off; it leaves the line, so offer Undo. */
    data object TurnOffWithUndo : SettingChipTap

    data object OpenThinking : SettingChipTap

    /** An MCP server that needs the user to sign in: start it ([serverName] is its stored name). */
    data class SignIn(val serverName: String) : SettingChipTap

    /**
     * An MCP server whose sign-in is pending ([SettingChip.Mcp.signInPending]): the chip opens its
     * own menu, "Cancel sign-in" and "Start over", so the screen does nothing with the tap.
     */
    data object SignInMenu : SettingChipTap

    /** An MCP chip whose sign-in can't start yet ([SettingChip.Mcp.waitsForSignIn]): nothing. */
    data object WaitForSignIn : SettingChipTap
}

/**
 * The tap rule for [chip] on this line. A server that needs sign-in signs in, and one whose sign-in
 * is pending opens its menu, even when the spec locks it on: the lock only says why it is there,
 * and signing in is what makes it work.
 */
fun SettingChips.tapFor(chip: SettingChip): SettingChipTap {
    val state = chip.state ?: return SettingChipTap.OpenThinking
    return when {
        chip is SettingChip.Mcp && chip.waitsForSignIn -> SettingChipTap.WaitForSignIn
        chip is SettingChip.Mcp && chip.opensMenu -> SettingChipTap.SignInMenu
        chip is SettingChip.Mcp && chip.signsInOnTap -> SettingChipTap.SignIn(chip.name)
        state == SettingChipState.LOCKED -> SettingChipTap.ExplainLock(lockedBy)
        chip is SettingChip.Tool && chip.pinned -> SettingChipTap.Toggle
        else -> SettingChipTap.TurnOffWithUndo
    }
}
