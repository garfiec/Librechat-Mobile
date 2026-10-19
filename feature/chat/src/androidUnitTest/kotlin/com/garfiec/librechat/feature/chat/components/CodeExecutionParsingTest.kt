package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.FunctionCall
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The live card parses `ActiveToolCall.input`/`output` (args read at completion) and the reloaded
 * card parses the persisted `AgentToolCall`. Both go through one string parser, so a reply reads
 * the same before and after it settles; these pin that the persisted overload adds nothing.
 */
class CodeExecutionParsingTest {

    private val argsJson = """{"lang":"py","code":"print(42)"}"""

    @Test
    fun `the persisted call parses like the live one when args is an object`() {
        val persisted = AgentToolCall(
            name = "execute_code",
            args = buildJsonObject {
                put("lang", "py")
                put("code", "print(42)")
            },
            output = "42\n",
        )

        assertThat(parseCodeExecution(persisted))
            .isEqualTo(parseCodeExecution("execute_code", argsJson, "42\n"))
        assertThat(parseCodeExecution(persisted)?.code).isEqualTo("print(42)")
        assertThat(parseCodeExecution(persisted)?.language).isEqualTo("py")
    }

    @Test
    fun `the persisted call parses like the live one when args is a JSON string`() {
        val persisted = AgentToolCall(name = "execute_code", args = JsonPrimitive(argsJson), output = "42")

        assertThat(parseCodeExecution(persisted)).isEqualTo(parseCodeExecution("execute_code", argsJson, "42"))
    }

    @Test
    fun `the legacy function arguments parse like the live args`() {
        val persisted = AgentToolCall(
            function = FunctionCall(name = "execute_code", arguments = argsJson, output = "42"),
        )

        assertThat(parseCodeExecution(persisted)).isEqualTo(parseCodeExecution("execute_code", argsJson, "42"))
    }

    @Test
    fun `a bash call's command is its code and bash its language`() {
        val result = parseCodeExecution("bash_tool", """{"command":"ls -la"}""", "total 0")

        assertThat(result?.code).isEqualTo("ls -la")
        assertThat(result?.language).isEqualTo("bash")
    }

    @Test
    fun `the generated files listing is left to the attachments`() {
        val result = parseCodeExecution("execute_code", argsJson, "done\n\nGenerated files:\n- chart.png")

        assertThat(result?.output).isEqualTo("done")
    }

    @Test
    fun `a program that prints JSON keeps it as stdout`() {
        val result = parseCodeExecution("execute_code", argsJson, """{"total": 3}""")

        assertThat(result?.output).isEqualTo("""{"total": 3}""")
    }

    @Test
    fun `a structured output is read for stdout, stderr and exit code`() {
        val result = parseCodeExecution("execute_code", argsJson, """{"stdout":"hi","stderr":"warn","exit_code":2}""")

        assertThat(result?.output).isEqualTo("hi")
        assertThat(result?.error).isEqualTo("warn")
        assertThat(result?.exitCode).isEqualTo(2)
    }

    @Test
    fun `a running call with empty args has no code yet`() {
        val result = parseCodeExecution("execute_code", "", null)

        assertThat(result?.code).isNull()
        assertThat(result?.output).isNull()
    }

    @Test
    fun `the running phase reads as booting only while the server says so`() {
        assertThat(codeExecutionPhase(ToolCallVerdict.RUNNING, sandboxStarting = true, result = null))
            .isEqualTo(CodeExecutionPhase.STARTING)
        assertThat(codeExecutionPhase(ToolCallVerdict.RUNNING, sandboxStarting = false, result = null))
            .isEqualTo(CodeExecutionPhase.RUNNING)
    }

    @Test
    fun `a settled call is never booting`() {
        val ok = parseCodeExecution("execute_code", argsJson, "42")

        assertThat(codeExecutionPhase(ToolCallVerdict.COMPLETED, sandboxStarting = true, result = ok))
            .isEqualTo(CodeExecutionPhase.FINISHED)
        assertThat(codeExecutionPhase(ToolCallVerdict.CANCELLED, sandboxStarting = true, result = ok))
            .isEqualTo(CodeExecutionPhase.CANCELLED)
        assertThat(codeExecutionPhase(ToolCallVerdict.FAILED, sandboxStarting = false, result = ok))
            .isEqualTo(CodeExecutionPhase.ERROR)
    }

    @Test
    fun `a completed step whose result failed reads as an error`() {
        val traceback = parseCodeExecution(
            "execute_code",
            argsJson,
            "Traceback (most recent call last):\n  File \"<stdin>\", line 1\nZeroDivisionError: division by zero",
        )
        val nonZeroExit = parseCodeExecution("execute_code", argsJson, """{"stdout":"","exit_code":1}""")
        val stderr = parseCodeExecution("execute_code", argsJson, """{"stdout":"x","stderr":"boom"}""")

        listOf(traceback, nonZeroExit, stderr).forEach {
            assertThat(codeExecutionPhase(ToolCallVerdict.COMPLETED, sandboxStarting = false, result = it))
                .isEqualTo(CodeExecutionPhase.ERROR)
        }
    }
}
