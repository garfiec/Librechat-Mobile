package com.garfiec.librechat.feature.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.tool_artifacts
import com.garfiec.librechat.feature.chat.resources.tool_code
import com.garfiec.librechat.feature.chat.resources.tool_file_search
import com.garfiec.librechat.feature.chat.resources.tool_memory
import com.garfiec.librechat.feature.chat.resources.tool_skills
import com.garfiec.librechat.feature.chat.resources.tool_url_context
import com.garfiec.librechat.feature.chat.resources.tool_web_search
import com.garfiec.librechat.feature.chat.resources.web_search_built_in
import com.garfiec.librechat.feature.chat.resources.web_search_built_in_plain
import com.garfiec.librechat.feature.chat.resources.web_search_checking
import com.garfiec.librechat.feature.chat.resources.web_search_librechat
import com.garfiec.librechat.feature.chat.util.WebSearchKind
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Single source of truth for the icon + label of the ephemeral chat tools, shared by the tools bottom
 * sheet ([ChatToolsSheetContent]) and the composer's settings line ([ComposerSettingsLine]) so the two
 * surfaces can't drift. Memory's glyph is not the brain, which means Thinking; Skills takes the
 * sparkle, as picked skills do, not MCP's puzzle piece. Returns null for keys mobile doesn't surface
 * as a toggle (e.g. `mcp`, MCP server names).
 */
internal data class EphemeralToolMeta(
    val icon: ImageVector,
    val titleRes: StringResource,
)

internal fun ephemeralToolMeta(toolKey: String): EphemeralToolMeta? = when (toolKey) {
    ToolConstants.WEB_SEARCH -> EphemeralToolMeta(Icons.Default.Search, Res.string.tool_web_search)
    ToolConstants.URL_CONTEXT -> EphemeralToolMeta(Icons.Default.Link, Res.string.tool_url_context)
    ToolConstants.FILE_SEARCH -> EphemeralToolMeta(Icons.Default.FindInPage, Res.string.tool_file_search)
    ToolConstants.CODE_INTERPRETER -> EphemeralToolMeta(Icons.Default.Code, Res.string.tool_code)
    ToolConstants.MEMORY -> EphemeralToolMeta(Icons.Default.AutoStories, Res.string.tool_memory)
    ToolConstants.SKILLS -> EphemeralToolMeta(Icons.Default.AutoAwesome, Res.string.tool_skills)
    ToolConstants.ARTIFACTS -> EphemeralToolMeta(Icons.Default.Layers, Res.string.tool_artifacts)
    else -> null
}

/** The name of the search [kind] runs, for the web-search control's second line; null when none can. */
@Composable
internal fun webSearchName(kind: WebSearchKind): String? = when (kind.route) {
    WebSearchRoute.NONE -> null
    WebSearchRoute.LIBRECHAT -> stringResource(Res.string.web_search_librechat)
    WebSearchRoute.CHECKING -> stringResource(Res.string.web_search_checking)
    WebSearchRoute.BUILT_IN -> kind.provider?.let { stringResource(Res.string.web_search_built_in, it.displayName) }
        ?: stringResource(Res.string.web_search_built_in_plain)
}
