package com.garfiec.librechat.feature.chat.components

/**
 * The attached-workspace `bash_tool`'s output, as upstream `parseCommandOutput`
 * (`client/src/components/Chat/Messages/Content/Parts/command.ts`) reads it against
 * `COMMAND_RESULT_TRAILER` (`packages/data-provider/src/previews.ts`):
 *
 *     stdout:\n<out>\n  stderr:\n<err>\n  [exit code: N][terminated by SIG][timed out][output truncated]
 *
 * `head + stderr + trailer` is the whole output, so a card can style each part without dropping text.
 */
data class CommandOutputParts(
    val exitCode: Int?,
    val signal: String?,
    val timedOut: Boolean,
    val truncated: Boolean,
    /** Non-zero exit, a terminating signal or a timeout. */
    val failed: Boolean,
    /** The stdout section (or the no-output notice), with any starting-directory header. */
    val head: String,
    /** The stderr section with its label; empty when there is none. */
    val stderr: String,
    val trailer: String,
)

/**
 * Parses [output], or null when it is not that shape. **Only call this for a call whose
 * server-stamped `executor` is `attached_workspace`**: the sandbox tool shares the name, so a
 * sandbox command that prints `[exit code: 1]` last has the same shape.
 *
 * `[A-Za-z0-9_+-]` stands in for JavaScript's ASCII `\w`, which ICU widens to every Unicode letter;
 * `[0-9]` for `\d` likewise. The trailer is anchored with `\z`, not `$`: JavaScript's `$` is the end
 * of the text, the JVM's also matches before a final newline.
 */
internal fun parseCommandOutput(output: String?): CommandOutputParts? {
    val text = output ?: return null
    val match = COMMAND_RESULT_TRAILER.find(text) ?: return null
    val body = text.substring(0, match.range.first + 1)
    val directoryLength = STARTING_DIRECTORY.find(body)?.value?.length ?: 0
    val content = body.substring(directoryLength)
    if (!content.startsWith(STDOUT) && !content.startsWith(STDERR) && content != EMPTY) return null
    var exitCode: Int? = null
    var signal: String? = null
    var timedOut = false
    var truncated = false
    MARKER.findAll(match.groupValues[1]).forEach { marker ->
        val code = marker.groups[2]?.value
        val name = marker.groups[3]?.value
        when {
            code != null -> exitCode = code.toIntOrNull()
            name != null -> signal = name
            marker.groupValues[1] == "timed out" -> timedOut = true
            else -> truncated = true
        }
    }
    // The last label wins, as upstream: output that itself prints `stderr:` is ambiguous, and
    // styling too little as stderr is the safer mistake. No text is dropped either way.
    val labelAt = if (content.startsWith(STDERR)) directoryLength else body.lastIndexOf("\n$STDERR") + 1
    val split = if (labelAt > 0 || content.startsWith(STDERR)) labelAt else body.length
    return CommandOutputParts(
        exitCode = exitCode,
        signal = signal,
        timedOut = timedOut,
        truncated = truncated,
        failed = (exitCode != null && exitCode != 0) || signal != null || timedOut,
        head = body.substring(0, split),
        stderr = body.substring(split),
        trailer = match.groupValues[1] + match.groupValues[2],
    )
}

/** Whether the attached command failed, or null when [output] is not the trailer's shape. */
internal fun attachedCommandFailed(output: String?): Boolean? = parseCommandOutput(output)?.failed

private const val STDOUT = "stdout:\n"
private const val STDERR = "stderr:\n"
private const val EMPTY = "Command completed with no output.\n"

private val COMMAND_RESULT_TRAILER = Regex(
    """\n((?:\[(?:exit code: -?[0-9]+|terminated by [A-Za-z0-9_+-]+|timed out|output truncated)\])+)""" +
        """((?:\nCommand reached timeoutMs: [^\n]*)?(?:\n\[directory hint: [^\n]*\])?)\z""",
)
private val STARTING_DIRECTORY = Regex("""^\[starting directory: "workspace/(?:[^"\\\r\n]|\\[^\r\n])*"\]\n""")
private val MARKER = Regex("""\[(exit code: (-?[0-9]+)|terminated by ([A-Za-z0-9_+-]+)|timed out|output truncated)\]""")
