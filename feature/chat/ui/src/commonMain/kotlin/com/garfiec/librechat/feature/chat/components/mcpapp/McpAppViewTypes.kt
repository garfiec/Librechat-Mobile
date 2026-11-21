package com.garfiec.librechat.feature.chat.components.mcpapp

enum class McpAppLoadFailure(val retryable: Boolean) {
    /** The read or validate failed for a reason worth retrying ("could not be loaded"). */
    COULD_NOT_LOAD(retryable = true),

    /** Nothing initialized within [McpAppHost.REVEAL_BUDGET_MS] ("failed to load"). */
    TIMED_OUT(retryable = true),

    /** A 400 from validate or read: the server's binding changed. Run the tool again. */
    BINDING_STALE(retryable = false),
}

/**
 * An action the App asked for that the user must approve first. Text is shown whole, never cut.
 *
 * [id] is unique per request within a host, so two identical requests in a row are still two
 * actions: approving one never settles the other.
 */
sealed interface McpAppAction {
    val id: Long
    val serverName: String

    data class ToolCall(
        override val id: Long,
        override val serverName: String,
        val toolName: String,
        val argumentsText: String,
    ) : McpAppAction

    data class Message(override val id: Long, override val serverName: String, val text: String) : McpAppAction
}
