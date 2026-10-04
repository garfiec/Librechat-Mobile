package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The live reply's text and reasoning so far, and their throttled flush to UI state.
 *
 * Reasoning is kept apart from the text so the live bubble can show it in a collapsed Thinking
 * block — the same place the persisted message renders its THINK parts — instead of as body text.
 * Both share one dirty flag and one flush.
 */
internal class StreamTextBuffer(private val handle: StreamingHandle) {
    private val text = StringBuilder()
    private val thinking = StringBuilder()
    private var dirty = false
    private var updateJob: Job? = null

    val currentText: String get() = text.toString()
    val currentThinking: String get() = thinking.toString()

    fun appendText(chunk: String) {
        text.append(chunk)
        dirty = true
    }

    fun appendThinking(chunk: String) {
        thinking.append(chunk)
        dirty = true
    }

    /**
     * Replaces both buffers from a resume snapshot, which is the authoritative state of the reply
     * so far. Reasoning lives in THINK parts' `think` field, not `text`, so reading `text` alone
     * would drop it from a resumed partial entirely.
     */
    fun replaceFrom(aggregatedContent: List<MessageContentPart>) {
        text.clear()
        aggregatedContent.filter { it.type != ContentType.THINK }
            .mapNotNull { it.text }
            .joinTo(text, separator = "")
        thinking.clear()
        aggregatedContent.filter { it.type == ContentType.THINK }
            .mapNotNull { it.think ?: it.text }
            .joinTo(thinking, separator = "")
        dirty = true
    }

    fun clear() {
        text.clear()
        thinking.clear()
        dirty = false
    }

    /**
     * Launches a periodic coroutine that flushes to UI state at most every
     * [STREAMING_UI_UPDATE_INTERVAL_MS] ms. This avoids recomposition spam from high-frequency SSE
     * chunks (each chunk would otherwise trigger a full state copy).
     */
    fun startUpdater() {
        updateJob?.cancel()
        updateJob = handle.scope.launch {
            while (isActive) {
                delay(STREAMING_UI_UPDATE_INTERVAL_MS)
                flush()
            }
        }
    }

    /** Stops the periodic updater and performs a final flush so the last chunk is never lost. */
    fun stopUpdater() {
        updateJob?.cancel()
        updateJob = null
        flush()
    }

    /** Writes the buffers to UI state if they changed since the last flush. */
    fun flush() {
        if (!dirty) return
        dirty = false
        handle.update {
            content = content.copy(
                streamingContent = text.toString(),
                streamingThinking = thinking.toString(),
            )
        }
    }

    private companion object {
        /** Minimum interval between streaming UI state updates to avoid recomposition spam. */
        const val STREAMING_UI_UPDATE_INTERVAL_MS = 50L
    }
}
