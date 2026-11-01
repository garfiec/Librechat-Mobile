package com.garfiec.librechat.core.model.content

/*
 * Encrypted reasoning kept off screen. Port of upstream `packages/data-provider/src/reasoning.ts`
 * (#17100, v0.8.9-rc1).
 *
 * Some providers return reasoning encrypted (Bedrock `redactedContent`, Anthropic
 * `redacted_thinking.data`, OpenAI `encrypted_content`, Gemini thought signatures), and the agents
 * SDK base64-encodes it into the THINK stream, so it arrives as one long unbroken run of
 * base64/base64url characters. Prose breaks on whitespace or punctuation far sooner.
 *
 * Every scan is a hand-written loop, not a Regex: a JVM-valid pattern can fail at class init under
 * Android's ICU engine, and these run on every reasoning delta.
 *
 * MIRRORED SERVER LOGIC — verify against upstream on version bumps (/sync-upstream).
 */

/** Shortest unbroken payload-alphabet run treated as an encoded payload rather than prose. */
const val OPAQUE_REASONING_MIN_RUN: Int = 128

/** Longest undecided tail released as a final word once its stream has ended. */
const val OPAQUE_REASONING_MAX_WORD: Int = 32

private const val THINK_OPEN = "<think>"
private const val THINK_CLOSE = "</think>"

/** `[A-Za-z0-9+/=_-]`: the base64 and base64url alphabets. */
private fun isPayloadChar(c: Char): Boolean =
    c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || c == '+' || c == '/' || c == '=' || c == '_' || c == '-'

/** [text] with every payload-alphabet run of at least [OPAQUE_REASONING_MIN_RUN] removed. */
private fun removePayloadRuns(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        if (!isPayloadChar(text[i])) {
            out.append(text[i])
            i++
            continue
        }
        val end = payloadRunEnd(text, i)
        if (end - i < OPAQUE_REASONING_MIN_RUN) out.append(text, i, end)
        i = end
    }
    return out.toString()
}

private fun payloadRunEnd(text: String, from: Int): Int {
    var end = from
    while (end < text.length && isPayloadChar(text[end])) end++
    return end
}

/** Persisted reasoning is stored inside `<think>` tags; they are not readable content. */
private fun withoutThinkTags(text: String): String = text.replace(THINK_OPEN, "").replace(THINK_CLOSE, "")

/**
 * [text] with encoded payloads removed, keeping readable prose. Empty when nothing but the
 * persisted `<think>` wrapper would remain; [text] itself when it holds no payload.
 */
fun stripOpaqueReasoning(text: String): String {
    val stripped = removePayloadRuns(text)
    if (stripped.length == text.length) return text
    return if (withoutThinkTags(stripped).isBlank()) "" else stripped
}

/** True when [text] has content but none of it is readable. */
fun isOpaqueReasoning(text: String): Boolean =
    withoutThinkTags(text).isNotBlank() && stripOpaqueReasoning(text).isEmpty()

/** Streaming position: an undecided trailing run, or a run already known to be a payload. */
data class OpaqueReasoningCursor(val tail: String = "", val opaque: Boolean = false)

/** Readable text settled by one [advanceOpaqueReasoning] step, and where the stream now stands. */
data class OpaqueReasoningStep(val readable: String, val cursor: OpaqueReasoningCursor)

/**
 * Feeds one reasoning [delta] through the payload filter. A payload can arrive split across deltas
 * shorter than the threshold, so a trailing run is held in the cursor until a non-payload character
 * settles it; once a run is known to be a payload it continues through the next non-payload
 * character, even across deltas, so no fragment of it is released as readable text.
 */
fun advanceOpaqueReasoning(cursor: OpaqueReasoningCursor, delta: String): OpaqueReasoningStep {
    val start = if (cursor.opaque) payloadRunEnd(delta, 0) else 0
    if (start == delta.length && cursor.opaque) return OpaqueReasoningStep("", cursor)
    val combined = (if (cursor.opaque) "" else cursor.tail) + delta.substring(start)
    var end = combined.length
    while (end > 0 && isPayloadChar(combined[end - 1])) end--
    val trailing = combined.substring(end)
    val readable = removePayloadRuns(combined.substring(0, end))
    return if (trailing.length >= OPAQUE_REASONING_MIN_RUN) {
        OpaqueReasoningStep(readable, OpaqueReasoningCursor(tail = "", opaque = true))
    } else {
        OpaqueReasoningStep(readable, OpaqueReasoningCursor(tail = trailing, opaque = false))
    }
}

/**
 * The undecided tail of a stream: a word-sized one is a final word, a longer one is more likely the
 * unclassified start of a payload and is withheld.
 */
fun flushOpaqueReasoning(cursor: OpaqueReasoningCursor): String =
    if (!cursor.opaque && cursor.tail.length <= OPAQUE_REASONING_MAX_WORD) cursor.tail else ""

/**
 * Reasoning text safe to show: payloads removed and, while [streaming], a trailing run that could
 * still grow into one held back until it is classified.
 */
fun readableReasoning(text: String, streaming: Boolean = false): String {
    if (!streaming) return stripOpaqueReasoning(text)
    val step = advanceOpaqueReasoning(OpaqueReasoningCursor(), text)
    return step.readable + flushOpaqueReasoning(step.cursor)
}

/**
 * How a THINK part renders: its readable text, or [encrypted] when it has content and none of it
 * can be shown (upstream's "Reasoning (encrypted)" marker). While [streaming], only a run already
 * classified as a payload counts as encrypted, so a long word still arriving is not mislabelled.
 */
data class ReasoningDisplay(val text: String, val encrypted: Boolean)

fun reasoningDisplay(raw: String, streaming: Boolean = false): ReasoningDisplay {
    if (!streaming) {
        val text = stripOpaqueReasoning(raw)
        return ReasoningDisplay(text, encrypted = isOpaqueReasoning(raw))
    }
    val step = advanceOpaqueReasoning(OpaqueReasoningCursor(), raw)
    val text = step.readable + flushOpaqueReasoning(step.cursor)
    return ReasoningDisplay(text, encrypted = step.cursor.opaque && withoutThinkTags(text).isBlank())
}
