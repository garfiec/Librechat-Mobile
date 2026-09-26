package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonElement

/**
 * Feature availability gates loaded from the server's `interface.*` config AND role
 * permissions (effective value = flag AND permission, matching the web client). Default
 * permissive (true) until both the RoleRepository and the interface config emit; fails open
 * when a flag is absent (older backends). Written only by [ChatViewModel]'s config load.
 */
@Immutable
data class FeatureGatesState(
    val promptsEnabled: Boolean = true,
    val promptsCreateEnabled: Boolean = true,
    val agentsEnabled: Boolean = true,
    val agentsCreateEnabled: Boolean = true,
    val mcpServersEnabled: Boolean = true,
    val multiConvoEnabled: Boolean = true,
    val temporaryChatEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val runCodeEnabled: Boolean = true,
    val fileSearchEnabled: Boolean = true,
    val bookmarksEnabled: Boolean = true,
    /**
     * Interface-only gates (no corresponding role permission on web). Driven solely by
     * the server's `interface.*` config: presets on `interface.presets && interface.modelSelect`,
     * model select on `interface.modelSelect`, parameters on `interface.parameters`.
     */
    val presetsEnabled: Boolean = true,
    val modelSelectEnabled: Boolean = true,
    val parametersEnabled: Boolean = true,
    /** `interface.feedback` (v0.8.8-rc2). False withholds the thumbs handler entirely, mirroring
     *  web's `useMessageActions`, rather than rendering the buttons disabled — that state means
     *  "not while streaming", and reusing it would tell the user to wait for something that is
     *  never coming. */
    val feedbackEnabled: Boolean = true,
    /** `interface.defaultPinnedTools` (v0.8.7): tool keys the server pins to the prompt bar.
     *  Raw, as sent; mapped/filtered to renderable chips by [ChatUiState.pinnedToolChips]. */
    val pinnedTools: List<String> = emptyList(),
    /** `endpointsDropParamsMap` (v0.8.8-rc3): parameters this server strips before calling the
     *  provider. Raw, as sent — the endpoint/model lookup is a union shape and is resolved inside
     *  [com.garfiec.librechat.core.ui.components.EndpointParameterRegistry]. */
    val dropParamsMap: Map<String, JsonElement>? = null,
    /** `/api/config.compactionEnabled` (v0.8.8-rc3). An announced capability, so absence is the
     *  gate on its own and nothing stacks a version check on it — a server that does not know the
     *  flag would answer a `compact` send as an ordinary empty turn. */
    val compactionEnabled: Boolean = false,
    /**
     * Context-usage gauge gate (v0.8.7). = `interface.contextUsage` AND backend ≥ 0.8.7.
     * Fails closed on older/unknown servers (the gauge has no data source there).
     */
    val contextUsageEnabled: Boolean = false,
    /**
     * Composer memory toggle. = MEMORIES USE+CREATE+UPDATE (the write set the inline
     * `set_memory`/`delete_memory` tools need) AND the agents endpoint's `memory` capability
     * AND the user not having opted out via `personalization.memories`. Mirrors web's
     * `useHasMemoryAccess` + `useAgentCapabilities().memoryEnabled` + opt-out check.
     */
    val memoryEnabled: Boolean = false,
    /**
     * Mid-run steering (v0.8.8): whether `POST /api/agents/chat/steer` exists on this server.
     *
     * Fails closed, and unlike the HITL pause this one *can* fail closed safely. A pause is
     * self-proving — the server pushed it, and hiding the card would strand the run — whereas
     * steering must be offered before any server has said anything about it. Closed here means
     * the composer keeps queueing mid-run, which is what mobile did before steering existed and
     * works against every supported server.
     *
     * Consequence of the date-gate's coverage window: a server built from an upstream commit
     * newer than the app's pin resolves to a null `DetectedBackend`, so steering hides there
     * until the next sync. Documented in VERSION_GATES.md.
     */
    val steeringSupported: Boolean = false,
    /**
     * Whether the read-only subagent child-thread routes are worth offering (v0.8.8-rc2).
     *
     * **Fail-SAFE, not fail-closed**: true unless the server is KNOWN to predate them, because the
     * affordance has to be decided before anything is requested and an unplaceable server is the
     * population most likely to have the routes. It gates the affordance only — the repository
     * asks the same question again before it issues a request, and a 404 from the route is never
     * a verdict about the server (see VERSION_GATES.md).
     *
     * Needed at all because `on_subagent_update` is v0.8.6: a 0.8.6/0.8.7 server renders subagent
     * trace cards, so without this the "View thread" row would appear on exactly the servers that
     * cannot serve it.
     */
    val subagentThreadsSupported: Boolean = false,
    /**
     * Whether follow-ups queued during an agents run go to the server's queue (v0.8.8-rc2) rather
     * than the local drain.
     *
     * **Fail-closed, and decided by version rather than by probing.** rc1 already stamps
     * `generationCreatedAt` on the run, so every other precondition holds there, but it has no
     * queued-turns route: its chat router takes the POST as `/:endpoint` and fails it without a
     * 404, so no capability fallback can ever latch and each follow-up becomes a row that never
     * sends and blocks the queue behind it. Closed means the local drain, which every supported
     * server handles.
     */
    val serverQueueSupported: Boolean = false,
    /**
     * The conversation whose trace `/availability` confirmed readable (v0.8.8-rc3).
     *
     * Held as the id rather than a Boolean so the entry point cannot survive a conversation
     * switch: the resolve is a network round trip, and a plain flag stays true across the gap
     * between leaving a traced conversation and the next answer arriving. Read through
     * [ChatUiState.traceViewerAvailable], which compares it to the conversation on screen.
     */
    val traceViewerConversationId: String? = null,
    /**
     * The detected backend version string (null while unresolved), for the handful of version
     * gates that live in pure state helpers rather than in a collector — currently the
     * shell-script MIME aliasing inside [ChatUiState.uploadRouteFor] and the .potx picker offer.
     * Written by the same `detectedBackend` collector that sets [steeringSupported].
     */
    val backendVersion: String? = null,
)

/**
 * Feature gates that flow into the composer ([ChatInput] → [ChatToolsSheetContent]),
 * bundled so they thread as one value across Android and iOS. Each defaults to true
 * (shown) so a default-constructed bundle is fully permissive. Built by
 * [ChatUiState.chatInputGates]; see [ChatUiState.modelSelectEnabled] /
 * [ChatUiState.parametersEnabled] / [ChatUiState.showEphemeralTools] /
 * [ChatUiState.fileUploadEnabled] for the individual gating rules.
 */
@Immutable
data class ChatInputGates(
    val modelSelectEnabled: Boolean = true,
    val parametersEnabled: Boolean = true,
    val showEphemeralTools: Boolean = true,
    val fileUploadEnabled: Boolean = true,
)
