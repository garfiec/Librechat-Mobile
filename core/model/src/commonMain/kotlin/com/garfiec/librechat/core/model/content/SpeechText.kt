package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.Message

/**
 * The text Read Aloud speaks for [message]: the answer the message view shows, never its
 * reasoning. Port of upstream `getSpeechText` (`packages/data-provider/src/parsers.ts`, #16790).
 *
 * - With content parts: the TEXT parts, joined by the server's rule (THINK and STEER skipped).
 * - Without: [Message.text] minus a legacy `:::thinking ... :::` block (see [LegacyThinking]).
 *
 * Not the editor prefill: editing a reply edits its reasoning too.
 *
 * MIRRORED SERVER LOGIC — verify against upstream on version bumps (/sync-upstream).
 */
fun speechTextOf(message: Message): String {
    val parts = message.content
    if (!parts.isNullOrEmpty()) return parts.flattenSearchableText(skipReasoning = true).trim()
    val text = message.text
    return (LegacyThinking.split(text)?.answer ?: text).trim()
}
