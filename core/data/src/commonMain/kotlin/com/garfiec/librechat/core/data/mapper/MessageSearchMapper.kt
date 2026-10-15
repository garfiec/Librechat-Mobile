package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.db.entity.MessageSearchEntity
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.content.flattenSearchableText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val partsSerializer = ListSerializer(MessageContentPart.serializer())

/**
 * The most text one message contributes to the search index. Ordinary messages are far below it,
 * so they keep parity with the server's index; the cap only stops a pasted multi-megabyte log
 * from costing a multi-megabyte tokenize on every write of its row.
 */
const val SEARCH_TEXT_MAX_CHARS: Int = 128_000

/**
 * What the search index holds for a message.
 *
 * @property text the visible text (TEXT and STEER parts), then the reasoning (THINK parts) after a
 *   newline: the same words the server's Meilisearch plugin indexes (`mongoMeili.ts`), with the
 *   reasoning moved to the end so one offset separates the two.
 * @property visibleLength where the reasoning starts in [text]; null when there is none, so the
 *   whole text is visible. A match at or past it is in the reasoning.
 */
data class IndexedText(val text: String?, val visibleLength: Int?)

/**
 * The [IndexedText] for a message: its content parts flattened with the server's rules, falling
 * back to `text` for a message with no parts (or only non-text ones), capped at
 * [SEARCH_TEXT_MAX_CHARS].
 *
 * Agent replies keep their text only in `content` (the row's `text` is empty), which is why the
 * plain `text` column alone can't be searched.
 */
fun searchTextOf(text: String?, contentJson: String?): IndexedText =
    indexedTextOf(text, contentJson?.let { runCatching { json.decodeFromString(partsSerializer, it) }.getOrNull() })

/** [searchTextOf] for already-decoded parts (a server hit carries them decoded). */
fun indexedTextOf(text: String?, parts: List<MessageContentPart>?): IndexedText {
    val visible = parts?.flattenSearchableText(includeSteer = true, skipReasoning = true).orEmpty()
    val reasoning = parts?.filter { it.type == ContentType.THINK }?.flattenSearchableText().orEmpty()
    if (visible.isBlank() && reasoning.isBlank()) return legacyIndexedText(text)
    if (reasoning.isBlank()) return IndexedText(visible.capForIndex(), visibleLength = null)
    val combined = (if (visible.isBlank()) reasoning else visible + REASONING_SEPARATOR + reasoning).capForIndex()
    val visibleLength = if (visible.isBlank()) 0 else minOf(visible.length, combined.length)
    return IndexedText(combined, visibleLength)
}

/** A message with no parts, whose reasoning may still sit in its text as a legacy block. */
private fun legacyIndexedText(text: String?): IndexedText {
    if (text == null) return IndexedText(null, visibleLength = null)
    val split = LegacyThinking.split(text) ?: return IndexedText(text.capForIndex(), visibleLength = null)
    val combined = (if (split.answer.isBlank()) split.reasoning else split.answer + REASONING_SEPARATOR + split.reasoning).capForIndex()
    val visibleLength = if (split.answer.isBlank()) 0 else minOf(split.answer.length, combined.length)
    return IndexedText(combined, visibleLength)
}

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

/** Joins the visible text to the reasoning, so a phrase can't run across the two. */
private const val REASONING_SEPARATOR = "\n"

/** The part of [searchText] a reader sees in the reply (no reasoning). */
fun visibleTextOf(searchText: String, visibleLength: Int?): String =
    if (visibleLength == null) searchText else searchText.substring(0, visibleLength.coerceIn(0, searchText.length))

/** [SEARCH_TEXT_MAX_CHARS] without splitting a surrogate pair at the cut. */
fun String.capForIndex(): String {
    if (length <= SEARCH_TEXT_MAX_CHARS) return this
    val end = if (this[SEARCH_TEXT_MAX_CHARS - 1].isHighSurrogate()) SEARCH_TEXT_MAX_CHARS - 1 else SEARCH_TEXT_MAX_CHARS
    return substring(0, end)
}

/**
 * The search side row for this message, or null for an unclaimed legacy row: an index entry with
 * no owner could surface under any account, so those are never indexed.
 */
fun MessageEntity.toSearchEntity(): MessageSearchEntity? {
    val owner = accountId ?: return null
    val indexed = searchTextOf(text, content)
    return MessageSearchEntity(
        messageId = messageId,
        accountId = owner,
        conversationId = conversationId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isCreatedByUser = isCreatedByUser,
        model = model,
        endpoint = endpoint,
        searchText = indexed.text,
        visibleLength = indexed.visibleLength,
    )
}
