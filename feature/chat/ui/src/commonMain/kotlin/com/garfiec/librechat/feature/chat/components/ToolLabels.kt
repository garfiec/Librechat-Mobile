package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.background.CHECK_BACKGROUND_TASK_TOOL
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.background_tasks_title
import com.garfiec.librechat.feature.chat.resources.picker_type_skill
import com.garfiec.librechat.feature.chat.resources.tool_name_ask_user_question
import com.garfiec.librechat.feature.chat.resources.tool_name_code
import com.garfiec.librechat.feature.chat.resources.tool_name_code_analysis
import com.garfiec.librechat.feature.chat.resources.tool_name_create_file
import com.garfiec.librechat.feature.chat.resources.tool_name_delete_memory
import com.garfiec.librechat.feature.chat.resources.tool_name_edit_file
import com.garfiec.librechat.feature.chat.resources.tool_name_file_search
import com.garfiec.librechat.feature.chat.resources.tool_name_image_edit
import com.garfiec.librechat.feature.chat.resources.tool_name_image_gen
import com.garfiec.librechat.feature.chat.resources.tool_name_read_file
import com.garfiec.librechat.feature.chat.resources.tool_name_set_memory
import com.garfiec.librechat.feature.chat.resources.tool_name_web_search
import org.jetbrains.compose.resources.StringResource

/**
 * A tool id's parts (upstream `parseToolName`, `client/src/utils/toolLabels.ts`): an MCP tool
 * `<tool>_mcp_<server>` splits into its server and tool, a native tool gets its user-readable
 * name, anything else keeps its raw name.
 *
 * Splits at the LAST delimiter, as upstream does when it has no server list to match against.
 */
internal data class ParsedToolName(val mcpServer: String?, val toolName: String, val friendlyName: StringResource?)

internal fun parseToolName(raw: String): ParsedToolName {
    val delimiter = raw.lastIndexOf(MCP_TOOL_DELIMITER)
    if (delimiter >= 0) {
        return ParsedToolName(
            mcpServer = raw.substring(delimiter + MCP_TOOL_DELIMITER.length),
            toolName = raw.substring(0, delimiter),
            friendlyName = null,
        )
    }
    return ParsedToolName(mcpServer = null, toolName = raw, friendlyName = TOOL_FRIENDLY_NAMES[raw])
}

internal const val MCP_TOOL_DELIMITER = "_mcp_"

/** Upstream `TOOL_FRIENDLY_NAME_KEYS`, in its order. */
private val TOOL_FRIENDLY_NAMES: Map<String, StringResource> = mapOf(
    ToolConstants.EXECUTE_CODE to Res.string.tool_name_code,
    ToolConstants.PROGRAMMATIC_TOOL_CALLING to Res.string.tool_name_code,
    ToolConstants.BASH_PROGRAMMATIC_TOOL_CALLING to Res.string.tool_name_code,
    ToolConstants.BASH_TOOL to Res.string.tool_name_code,
    CHECK_BACKGROUND_TASK_TOOL to Res.string.background_tasks_title,
    ToolConstants.WEB_SEARCH to Res.string.tool_name_web_search,
    "image_gen_oai" to Res.string.tool_name_image_gen,
    "image_edit_oai" to Res.string.tool_name_image_edit,
    "gemini_image_gen" to Res.string.tool_name_image_gen,
    ToolConstants.FILE_SEARCH to Res.string.tool_name_file_search,
    ToolConstants.CODE_INTERPRETER to Res.string.tool_name_code_analysis,
    ToolConstants.RETRIEVAL to Res.string.tool_name_file_search,
    ToolConstants.ASK_USER_QUESTION to Res.string.tool_name_ask_user_question,
    FileToolKind.CREATE.toolName to Res.string.tool_name_create_file,
    ToolConstants.SET_MEMORY to Res.string.tool_name_set_memory,
    FileToolKind.EDIT.toolName to Res.string.tool_name_edit_file,
    ToolConstants.DELETE_MEMORY to Res.string.tool_name_delete_memory,
    "skill" to Res.string.picker_type_skill,
    FileToolKind.READ.toolName to Res.string.tool_name_read_file,
)
