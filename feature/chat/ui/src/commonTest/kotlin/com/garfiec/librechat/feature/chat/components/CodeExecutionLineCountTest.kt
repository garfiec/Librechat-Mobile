package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.content.AgentToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "Show all N lines" names the output's own count, never a server preview's. */
class CodeExecutionLineCountTest {

    private val line = "LONGWORDXYZ-".repeat(80)
    private val output = "[starting directory: \"workspace/\"]\nstdout:\n$line\nstderr:\nyes: stdout: Broken pipe\n\n[exit code: 0]"

    private fun shownText(call: AgentToolCall): String {
        val result = requireNotNull(parseCodeExecution(call))
        val trailer = result.command?.trailer?.trim()?.ifEmpty { null }
        return listOfNotNull(result.output, result.error, trailer).joinToString("\n")
    }

    private val full = AgentToolCall(
        id = "toolu_1",
        name = "bash_tool",
        args = null,
        output = output,
        executor = AgentToolCall.EXECUTOR_ATTACHED_WORKSPACE,
    )

    @Test
    fun the_full_output_counts_its_own_lines() {
        assertEquals(6, outputLineCount(shownText(full), isPreview = false))
    }

    @Test
    fun a_preview_names_no_count() {
        val preview = full.copy(
            output = output.take(254) + "\n…\n" + output.takeLast(255),
            outputTruncated = true,
            outputLength = output.length,
        )
        // The cut lands inside the long stdout line and adds the marker's own line: 8, not 6.
        assertEquals(8, shownText(preview).lines().size)
        assertNull(outputLineCount(shownText(preview), isPreview = true))
    }
}
