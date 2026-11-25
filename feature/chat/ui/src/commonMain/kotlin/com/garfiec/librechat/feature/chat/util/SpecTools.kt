package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.config.ModelSpec

/**
 * The tools a spec chat holds on top of what [spec] locks. They count only while [spec] is the
 * selection, so any change of spec (whoever writes it) starts the next one from its own tools, as
 * web's `applyModelSpecEphemeralAgent` resets the ephemeral agent to the spec's values. Remembered
 * per conversation on this device (`ConversationWebSearchStore.specTools`), as web layers a chat's
 * stored toggles over the spec's on reopen. The user's own toggles (`enabledTools`, the MCP
 * selection, web search and URL context in the parameters) stay untouched under a spec, which is what
 * brings them back on leaving it.
 *
 * [added] uses the toolbar's keys, web search and URL context included. A spec chat with none yet
 * starts from the preset's own ([presetTools]) and, where the server lets the user turn them off
 * (`ModelSelectionState.specToolsAreDefaults`), the spec's own tools and MCP servers.
 */
@Immutable
data class SpecTools(
    val spec: String,
    val added: Set<String> = emptySet(),
    val addedMcp: Set<String> = emptySet(),
)

/**
 * The toolbar keys this spec turns on. MIRRORED from the server's spec tool resolution
 * (`packages/api/src/agents/toggles.ts`, registered in `scripts/mirrors.json` as `model-spec-tools`):
 * from v0.8.9 each is a default an explicit request flag overrides, except under a spec that
 * withholds the controls (`hideBadgeRow`); an older server ORs each with the request's, so the user
 * can't turn these off there. `askUserQuestion` has no toolbar key and is applied by the server alone.
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
 * can turn them off, and under it the server sends the preset whatever the chat holds. They share a
 * toolbar key with [forcedTools]' web search, which is LibreChat's search, not the provider's:
 * [webSearchRoute] tells the two apart from the spec itself.
 */
internal val ModelSpec.presetTools: Set<String>
    get() = buildSet {
        if (preset?.webSearch == true) add(ToolConstants.WEB_SEARCH)
        if (preset?.urlContext == true) add(ToolConstants.URL_CONTEXT)
    }

/**
 * The Artifacts toggle while the spec sets an artifacts mode. The request sends the spec's mode
 * ([ModelSpec.artifactsMode]), which a server before v0.8.9 reads only from there; the toggle
 * follows [forcedTools].
 */
internal val ModelSpec.specArtifacts: Set<String>
    get() = if (artifactsMode != null) setOf(ToolConstants.ARTIFACTS) else emptySet()

/** The MCP servers this spec starts a chat with, by the same rule as [forcedTools]. */
internal val ModelSpec.forcedMcpServers: Set<String>
    get() = mcpServers.orEmpty().toSet()
