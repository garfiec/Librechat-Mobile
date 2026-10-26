package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.config.ModelSpec

/**
 * The tools a user added on top of [spec] in this chat. They count only while [spec] is the
 * selection, so any change of spec (whoever writes it) starts the next one from its own tools, as
 * web's `applyModelSpecEphemeralAgent` resets the ephemeral agent to the spec's values. Never
 * persisted: the user's own toggles (`enabledTools`, the MCP selection, web search and URL context
 * in the parameters) stay untouched under a spec, which is what brings them back on leaving it.
 *
 * [added] uses the toolbar's keys, web search and URL context included. A spec chat with none yet
 * starts from the preset's own ([presetTools]).
 */
@Immutable
data class SpecTools(
    val spec: String,
    val added: Set<String> = emptySet(),
    val addedMcp: Set<String> = emptySet(),
)

/**
 * The toolbar keys this spec turns on. MIRRORED from `loadEphemeralAgent`
 * (`packages/api/src/agents/load.ts`, registered in `scripts/mirrors.json` as `model-spec-tools`):
 * the server ORs each flag with the request's, so the user can't turn these off.
 * `askUserQuestion` has no toolbar key and is applied by the server alone.
 */
internal val ModelSpec.forcedTools: Set<String>
    get() = buildSet {
        if (webSearch == true) add(ToolConstants.WEB_SEARCH)
        if (fileSearch == true) add(ToolConstants.FILE_SEARCH)
        if (executeCode == true) add(ToolConstants.CODE_INTERPRETER)
        if (memory == true) add(ToolConstants.MEMORY)
    }

/**
 * The preset's own `web_search` and `url_context`, the provider's grounding parameters. Web copies
 * the preset into the conversation, so a spec chat starts with these on; without `enforce` the user
 * can turn them off, and under it the server sends the preset whatever the chat holds.
 */
internal val ModelSpec.presetTools: Set<String>
    get() = buildSet {
        if (preset?.webSearch == true) add(ToolConstants.WEB_SEARCH)
        if (preset?.urlContext == true) add(ToolConstants.URL_CONTEXT)
    }

/** The MCP servers this spec adds to every request, the same way. */
internal val ModelSpec.forcedMcpServers: Set<String>
    get() = mcpServers.orEmpty().toSet()
