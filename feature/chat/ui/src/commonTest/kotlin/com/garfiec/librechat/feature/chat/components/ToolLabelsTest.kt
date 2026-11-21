package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.tool_name_code
import com.garfiec.librechat.feature.chat.resources.tool_name_read_file
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Upstream `parseToolName` (`client/src/utils/toolLabels.ts`) without a server list. */
class ToolLabelsTest {

    @Test
    fun an_mcp_tool_splits_at_the_last_delimiter() {
        val parsed = parseToolName("search_code_mcp_github")
        assertEquals("github", parsed.mcpServer)
        assertEquals("search_code", parsed.toolName)
        assertNull(parsed.friendlyName)
        assertEquals("a_mcp_b", parseToolName("a_mcp_b_mcp_srv").toolName)
    }

    @Test
    fun a_native_tool_gets_its_friendly_name() {
        assertEquals(Res.string.tool_name_code, parseToolName("bash_tool").friendlyName)
        assertEquals(Res.string.tool_name_code, parseToolName("run_tools_with_code").friendlyName)
        assertEquals(Res.string.tool_name_read_file, parseToolName("read_file").friendlyName)
        assertNull(parseToolName("bash_tool").mcpServer)
    }

    @Test
    fun an_unknown_tool_keeps_its_name() {
        val parsed = parseToolName("my_tool")
        assertEquals("my_tool", parsed.toolName)
        assertNull(parsed.friendlyName)
    }
}
