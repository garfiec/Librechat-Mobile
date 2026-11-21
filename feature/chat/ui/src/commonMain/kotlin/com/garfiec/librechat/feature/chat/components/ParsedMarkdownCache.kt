package com.garfiec.librechat.feature.chat.components

import com.mikepenz.markdown.model.State

/**
 * Content-hash-keyed cache of parsed markdown [State.Success] AST. The m3
 * renderer parses asynchronously; when a LazyColumn item is disposed and later
 * scrolled back into view its remembered state is gone and parsing restarts
 * from scratch. During the parse window the rendered height is 0, then jumps
 * to the final value, and adjacent inline-artifact slots get pushed down — the
 * visible scroll jump.
 *
 * Caching the parsed [State.Success] lets [CachedMarkdown] render directly via
 * the `Markdown(state, ...)` overload on re-entry, bypassing the library's
 * Loading→Success transition entirely. The cache is hoisted in `ChatViewModel`
 * and provided through `ChatRoot`.
 *
 * Memory cost: an AST is roughly proportional to the markdown source; 200
 * messages of ~500 chars each is in the tens of KB range — negligible for a
 * chat session.
 */
class ParsedMarkdownCache(maxEntries: Int = MAX_ENTRIES_DEFAULT) :
    LruSnapshotCache<State.Success>(maxEntries) {

    private companion object {
        const val MAX_ENTRIES_DEFAULT = 200
    }
}

fun parsedMarkdownCacheKey(content: String): String =
    content.hashCode().toString(16)
