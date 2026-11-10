package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.model.speech.voiceFor

/**
 * Upstream's default `speech.tts.maxInputChars` (`DEFAULT_TTS_MAX_INPUT_CHARS`,
 * `packages/api/src/files/speech.ts`, v0.8.9-rc1). The server reads only this many characters of a
 * `tts/manual` request and drops the rest without an error, so a longer reply sent whole stops
 * partway. Operators can lower it, and it is not in the startup config.
 */
internal const val SERVER_TTS_MAX_INPUT_CHARS = 20_000

/**
 * How much of a reply one server read-aloud request carries. Well under
 * [SERVER_TTS_MAX_INPUT_CHARS], so a cap an operator lowered somewhat still reads the whole reply,
 * and short enough that the first audio starts without waiting on a whole long reply's synthesis.
 */
internal const val SERVER_TTS_SEGMENT_CHARS = 4_000

/**
 * Upstream's sentence separators (`SEPARATORS`, `packages/data-provider/src/parsers.ts`), the
 * boundaries its own streamed speech splits on.
 */
internal val SPEECH_SEPARATORS = listOf(".", "?", "!", "۔", "。", "‥", ";", "¡", "¿", "\n", "```")

/**
 * [text] cut into pieces of at most [maxChars] for server speech, each ending at the last sentence
 * separator in its window (upstream `findLastSeparatorIndex`), else the last space, else a hard cut
 * that never splits a surrogate pair. Joined, the pieces are exactly [text]. Blank pieces are dropped.
 */
internal fun splitForSpeech(text: String, maxChars: Int = SERVER_TTS_SEGMENT_CHARS): List<String> {
    require(maxChars > 1)
    if (text.length <= maxChars) return listOf(text).filter { it.isNotBlank() }
    val segments = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        if (text.length - start <= maxChars) {
            segments += text.substring(start)
            break
        }
        val window = text.substring(start, start + maxChars)
        val cut = separatorCut(window) ?: spaceCut(window) ?: hardCut(window)
        segments += window.substring(0, cut)
        start += cut
    }
    return segments.filter { it.isNotBlank() }
}

/** Just past the last separator in [window], or null when there is none past its start. */
private fun separatorCut(window: String): Int? {
    var best = -1
    for (separator in SPEECH_SEPARATORS) {
        val index = window.lastIndexOf(separator)
        if (index >= 0) best = maxOf(best, index + separator.length)
    }
    return best.takeIf { it > 0 && it <= window.length }
}

private fun spaceCut(window: String): Int? = window.lastIndexOf(' ').takeIf { it > 0 }?.plus(1)

private fun hardCut(window: String): Int =
    if (window.last().isHighSurrogate()) window.length - 1 else window.length

/**
 * Asks the server for [text] one [splitForSpeech] segment at a time, in order, handing each
 * segment's audio to [onSegment] (with whether it is the last) before requesting the next. Stops at
 * the first failed request and returns it; returns null when every segment was handed over, or
 * when [onSegment] returned false to end the read early.
 */
internal suspend fun <T> synthesizeInSegments(
    text: String,
    synthesize: suspend (String) -> Result<T>,
    onSegment: suspend (segment: String, audio: T, isLast: Boolean) -> Boolean,
): Result.Error? {
    val segments = splitForSpeech(text, SERVER_TTS_SEGMENT_CHARS)
    segments.forEachIndexed { index, segment ->
        when (val result = synthesize(segment)) {
            is Result.Success -> if (!onSegment(segment, result.data, index == segments.lastIndex)) return null
            is Result.Error -> return result
            is Result.Loading -> Unit
        }
    }
    return null
}

/**
 * The server voice to request, [voiceFor] the offered list, so a long reply keeps one voice across
 * segments. A failed voices read keeps the stored one.
 */
internal suspend fun serverVoiceToUse(stored: String?, voices: suspend () -> Result<List<TtsVoice>>): String? =
    when (val result = voices()) {
        is Result.Success -> result.data.voiceFor(stored)?.id
        else -> stored
    }
