package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.garfiec.librechat.core.model.content.CitationIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [text] parsed into [MarkdownSegment]s with citations normalized against the turn's search results
 * ([LocalMarkdownMedia]), so the segments hold only citations that resolve, ready to draw as pills.
 * With [withoutCitations] every citation is removed instead: find-in-page draws its own strings, which
 * have no pills, and its match numbering ([countMarkdownOccurrences]) counts the same stripped text.
 *
 * While [streaming], parsing and normalizing run off the main thread with the previous result
 * retained (and the first text done synchronously) so the bubble never blanks. A new citation index
 * re-derives the same text.
 */
@Composable
internal fun rememberCitedMarkdownSegments(
    text: String,
    streaming: Boolean,
    withoutCitations: Boolean,
): List<MarkdownSegment> {
    val index = if (withoutCitations) CitationIndex.Empty else LocalMarkdownMedia.current.citations
    if (!streaming) {
        val segments = remember(text) { parseMarkdownSegments(text) }
        return remember(segments, index) { segments.withCitations(index, streaming = false) }
    }

    val latest by rememberUpdatedState(text to index)
    val initial = remember { text to index }
    var segments by remember { mutableStateOf(streamedSegments(initial)) }

    LaunchedEffect(Unit) {
        collectDerived(
            inputs = snapshotFlow { latest },
            alreadyDerived = initial,
            derive = ::streamedSegments,
        ) { segments = it }
    }

    return segments
}

internal fun streamedSegments(input: Pair<String, CitationIndex>): List<MarkdownSegment> =
    parseMarkdownSegments(input.first, streaming = true).withCitations(input.second, streaming = true)

/**
 * Parses the latest text from [texts] on [parseContext]. [parseContext] must differ from the
 * caller's dispatcher, or a cancelled parse publishes its result instead of being discarded.
 */
internal suspend fun collectMarkdownSegments(
    texts: Flow<String>,
    alreadyParsed: String,
    parse: (String) -> List<MarkdownSegment> = ::parseMarkdownSegments,
    parseContext: CoroutineContext = Dispatchers.Default,
    onSegments: (List<MarkdownSegment>) -> Unit,
) = collectDerived(texts, alreadyParsed, parse, parseContext, onSegments)

/**
 * Derives a value from the latest input in [inputs] on [deriveContext], skipping inputs equal to
 * the previous one. [deriveContext] must differ from the caller's dispatcher, or a cancelled
 * derivation publishes its result instead of being discarded.
 */
internal suspend fun <I, O> collectDerived(
    inputs: Flow<I>,
    alreadyDerived: I,
    derive: (I) -> O,
    deriveContext: CoroutineContext = Dispatchers.Default,
    onResult: (O) -> Unit,
) {
    var lastInput = alreadyDerived
    inputs.conflate().collect { input ->
        if (input == lastInput) return@collect
        val result = withContext(deriveContext) { derive(input) }
        onResult(result)
        lastInput = input
    }
}
