package com.garfiec.librechat.core.model.content

/**
 * Reasoning saved inside a message's text, from before reasoning had its own content part:
 * `:::thinking\n…\n:::` and then the answer. The web client splits it the same way
 * (`parseThinkingContent`, `MessageContent.tsx`): the first such block is the reasoning, and the
 * text without it, trimmed, is the answer.
 */
object LegacyThinking {
    private val BLOCK = Regex(":::thinking([\\s\\S]*?):::")

    class Split(val answer: String, val reasoning: String)

    /** The answer and the reasoning, or null when [text] holds no thinking block. */
    fun split(text: String): Split? {
        if (!text.contains(MARKER)) return null
        val match = BLOCK.find(text) ?: return null
        return Split(answer = text.removeRange(match.range).trim(), reasoning = match.groupValues[1].trim())
    }

    private const val MARKER = ":::thinking"
}
