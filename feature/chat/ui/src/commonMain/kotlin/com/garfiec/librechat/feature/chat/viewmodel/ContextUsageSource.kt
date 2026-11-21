package com.garfiec.librechat.feature.chat.viewmodel

/**
 * Where the gauge's current [MessagesState.contextUsage] came from. Every writer stamps it
 * alongside the value, so a reader can tell a live reading from one the gauge computed itself.
 */
enum class ContextUsageSource {
    /** `on_context_usage` from the stream, or the resume frame's latest reading. */
    LIVE,

    /** The snapshot the server saved on a response (`metadata.contextUsage`, v0.8.8). */
    SNAPSHOT,

    /** Computed on the device from the branch's messages; shown marked as an estimate. */
    ESTIMATE,

    /** The context-projection endpoint, on servers before v0.8.8-rc1. */
    PROJECTION,
}
