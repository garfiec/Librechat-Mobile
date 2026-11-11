package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.speechTextOf
import com.garfiec.librechat.core.model.content.stripCitations

/**
 * The link a notification opens. Must match the `conversation` branch of `DeepLinks.resolve` in
 * `:shared` (its tests round-trip this), which refuses the link unless [accountId] is active.
 */
fun conversationLink(conversationId: String, accountId: AccountId): String =
    "librechat://conversation/$conversationId?account=${percentEncode(accountId.value)}"

/** The Schedules list, for a scheduled-chat notification with no chat to open. */
fun schedulesLink(accountId: AccountId): String = "librechat://schedules?account=${percentEncode(accountId.value)}"

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

private fun percentEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val char = (byte.toInt() and 0xFF).toChar()
        if (char in UNRESERVED) append(char) else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}

const val PREVIEW_MAX_CHARS = 180

private val FENCE = Regex("^\\s*(```|~~~).*$", RegexOption.MULTILINE)
private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val LINE_MARKER = Regex("^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+[.)]\\s+)", RegexOption.MULTILINE)
private val EMPHASIS = Regex("(\\*\\*|__|\\*|`)")
private val WHITESPACE = Regex("\\s+")

/**
 * A reply's text as a notification shows it: the answer without reasoning or citation markers,
 * markdown syntax removed, on one line, cut at a word near [PREVIEW_MAX_CHARS]. Blank for a reply
 * with no text (an image or a tool run); the notifier shows a generic body then.
 */
fun notificationPreviewOf(message: Message): String {
    val plain = stripCitations(speechTextOf(message))
        .replace(FENCE, "")
        .replace(IMAGE, "")
        .replace(LINK, "$1")
        .replace(LINE_MARKER, "")
        .replace(EMPHASIS, "")
        .replace(WHITESPACE, " ")
        .trim()
    if (plain.length <= PREVIEW_MAX_CHARS) return plain
    val cut = plain.take(PREVIEW_MAX_CHARS)
    val atWord = cut.substringBeforeLast(' ').takeIf { it.length > PREVIEW_MAX_CHARS / 2 } ?: cut
    return atWord.trimEnd() + "…"
}
