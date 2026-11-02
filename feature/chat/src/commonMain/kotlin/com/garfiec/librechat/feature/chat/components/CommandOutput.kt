package com.garfiec.librechat.feature.chat.components

/**
 * The verdict the attached-workspace `bash_tool` writes at the end of its output, as upstream
 * `parseCommandOutput` (`client/src/components/Chat/Messages/Content/Parts/command.ts`) reads it
 * against `COMMAND_RESULT_TRAILER` (`packages/data-provider/src/previews.ts`):
 *
 *     stdout:\n<out>\n  stderr:\n<err>\n  [exit code: N][terminated by SIG][timed out][output truncated]
 *
 * Only the verdict is ported; the card's stdout/stderr split is not. **Only call this for a call
 * whose server-stamped `executor` is `attached_workspace`**: the sandbox tool shares the name, so a
 * sandbox command that prints `[exit code: 1]` last has the same shape.
 *
 * Returns null when the output is not that shape, else whether the command failed (a non-zero
 * exit, a terminating signal or a timeout). `[A-Za-z0-9_+-]` stands in for JavaScript's ASCII `\w`,
 * which ICU widens to every Unicode letter; `[0-9]` for `\d` likewise. The trailer is anchored with
 * `\z`, not `$`: JavaScript's `$` is the end of the text, the JVM's also matches before a final newline.
 */
internal fun attachedCommandFailed(output: String?): Boolean? {
    val text = output ?: return null
    val match = COMMAND_RESULT_TRAILER.find(text) ?: return null
    val body = text.substring(0, match.range.first + 1)
    val directoryLength = STARTING_DIRECTORY.find(body)?.value?.length ?: 0
    val content = body.substring(directoryLength)
    if (!content.startsWith(STDOUT) && !content.startsWith(STDERR) && content != EMPTY) return null
    val markers = match.groupValues[1]
    var failed = false
    MARKER.findAll(markers).forEach { marker ->
        val code = marker.groups[2]?.value
        when {
            code != null -> if (code.toIntOrNull() != 0) failed = true
            marker.groups[3] != null -> failed = true
            marker.groupValues[1] == "timed out" -> failed = true
        }
    }
    return failed
}

private const val STDOUT = "stdout:\n"
private const val STDERR = "stderr:\n"
private const val EMPTY = "Command completed with no output.\n"

private val COMMAND_RESULT_TRAILER = Regex(
    """\n((?:\[(?:exit code: -?[0-9]+|terminated by [A-Za-z0-9_+-]+|timed out|output truncated)\])+)""" +
        """((?:\nCommand reached timeoutMs: [^\n]*)?(?:\n\[directory hint: [^\n]*\])?)\z""",
)
private val STARTING_DIRECTORY = Regex("""^\[starting directory: "workspace/(?:[^"\\\r\n]|\\[^\r\n])*"\]\n""")
private val MARKER = Regex("""\[(exit code: (-?[0-9]+)|terminated by ([A-Za-z0-9_+-]+)|timed out|output truncated)\]""")
