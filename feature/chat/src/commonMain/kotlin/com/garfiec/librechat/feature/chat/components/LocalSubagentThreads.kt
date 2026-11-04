package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the child-thread viewer (v0.8.8-rc2), supplied by [ChatRoot].
 *
 * Takes the parent `subagent` tool_call id of the card that asked, or null to open on the list.
 * That id is NOT a thread id — `threadId` exists only in the parent index — so the viewer fetches
 * the index first and resolves the join itself. The card therefore cannot deep-link on its own,
 * which is also why the viewer opens on a LIST: an event-spawned child has no tool call and no
 * card, so the list is the only place it can ever be reached from.
 *
 * A composition local for the same reason as [LocalSubagentProgress]: the trace card sits several
 * renderer signatures deep, and threading a callback down all of them to reach it would touch
 * every content renderer for one affordance.
 *
 * Defaults to a no-op so previews and any composition outside the chat screen simply do nothing.
 */
val LocalSubagentThreads = staticCompositionLocalOf<(parentToolCallId: String?) -> Unit> { {} }

/**
 * Opens the viewer straight onto one child thread by its id. A background subagent task's poll
 * result carries `subagent_thread_id` (v0.8.9-rc1), the one place outside the index that names a
 * thread, so its card can deep-link where a trace card cannot. Gated by
 * [LocalSubagentThreadsAvailable] like [LocalSubagentThreads].
 */
val LocalOpenSubagentThread = staticCompositionLocalOf<(threadId: String) -> Unit> { {} }

/** Whether the viewer is available at all — false outside the chat screen and on an old server. */
val LocalSubagentThreadsAvailable = staticCompositionLocalOf { false }
