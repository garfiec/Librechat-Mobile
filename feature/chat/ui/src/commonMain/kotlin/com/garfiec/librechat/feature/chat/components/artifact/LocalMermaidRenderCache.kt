package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Composition-scoped mermaid SVG cache. `static` because the cache instance
 * reference is stable for the chat session — only its internal map contents
 * mutate, and those go through [SnapshotStateMap] which observes reads
 * independently.
 *
 * The default `error(...)` initializer forces callers to wrap chat content in
 * `ChatRoot { }`. Tests that exercise composables consuming the cache can
 * provide a fresh `MermaidRenderCache()` via `CompositionLocalProvider` in the
 * test setup.
 */
val LocalMermaidRenderCache = staticCompositionLocalOf<MermaidRenderCache> {
    error("LocalMermaidRenderCache not provided; wrap chat content in ChatRoot { }")
}
