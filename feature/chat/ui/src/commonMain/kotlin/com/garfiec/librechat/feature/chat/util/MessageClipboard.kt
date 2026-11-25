package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.CitationClipboard
import com.garfiec.librechat.core.model.content.CitationIndex
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.components.systemEventFor

/**
 * A message's clipboard text, mirroring web `getMessageParts` and `getMessageClipboardSource`
 * (`client/src/hooks/Messages/useCopyToClipboard.ts`, v0.8.9): only the answer's text parts, joined
 * by newlines, with inline citations numbered and a sources footer. Reasoning, tool calls and media
 * are not copied. A wake-up copies its tasks' results, never the hidden prompt it is stored as.
 *
 * ERROR parts copy their error text, unlike web, so Copy on a failed reply is not a silent no-op.
 */
fun serializeMessageForClipboard(message: Message): String {
    systemEventFor(message, isEditing = false)?.let { wakeup ->
        return wakeup.tasks.joinToString("\n\n") { it.result }
    }
    val citations = CitationClipboard(CitationIndex.from(message.attachments))
    val parts = message.content
    val texts = if (parts.isNullOrEmpty()) {
        listOf(citations.process(message.text))
    } else {
        parts.mapNotNull { clipboardTextOf(it, citations) }
    }
    return (texts.filter { it.isNotBlank() } + listOfNotNull(citations.footer())).joinToString("\n")
}

private fun clipboardTextOf(part: MessageContentPart, citations: CitationClipboard): String? = when (part.type) {
    ContentType.ERROR -> part.error ?: part.text
    ContentType.TEXT, ContentType.TEXT_DELTA -> part.text?.takeIf { it.isNotBlank() }?.let(citations::process)
    else -> null
}
