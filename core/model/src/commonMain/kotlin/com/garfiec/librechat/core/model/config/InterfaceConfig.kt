package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
data class InterfaceConfig(
    val privacyPolicy: PrivacyPolicyConfig? = null,
    val termsOfService: TermsOfServiceConfig? = null,
    val endpointsMenu: Boolean = true,
    val modelSelect: Boolean = true,
    val parameters: Boolean = true,
    val presets: Boolean = true,
    val sidePanel: Boolean = true,
    val bookmarks: Boolean = true,
    val prompts: JsonElement? = null,
    val agents: JsonElement? = null,
    val multiConvo: Boolean = true,
    val memories: Boolean = true,
    val temporaryChat: Boolean = true,
    val runCode: Boolean = true,
    val webSearch: Boolean = true,
    val fileSearch: Boolean = true,
    val fileCitations: Boolean = true,
    val customWelcome: String? = null,
    val remoteAgents: JsonElement? = null,
    // --- v0.8.6 detection (parse-surface only; no gating UI yet) ---
    /** Skills feature toggle. `bool | { use, create, share, public, defaultActiveOnShare }`.
     *  Kept as raw JSON for forward-compat, mirroring [prompts] / [agents] / [remoteAgents]. */
    val skills: JsonElement? = null,
    /** When true, the server exposes build metadata for an About-screen display. */
    val buildInfo: Boolean? = null,
    /** Whether the web client auto-submits a prompt passed via URL. No mobile counterpart. */
    val autoSubmitFromUrl: Boolean? = null,
    /** Temp-chat retention mode: `"all"` | `"temporary"`. Cosmetic on mobile (no temp-chat surface). */
    val retentionMode: String? = null,
    // --- v0.8.7 detection / gating ---
    /** Whether the context-usage gauge is enabled. Server default is `true`. Gates the
     *  chat-screen context gauge (combined with a `BackendVersion.isCompatibleOrNewer("0.8.7-rc1")` check). */
    val contextUsage: Boolean = true,
    /** Whether the gauge surfaces USD cost alongside token usage. Server default is `false`.
     *  Retained for wire fidelity (real `/api/config` key); not read yet — the cost readout
     *  isn't built. */
    val contextCost: Boolean = false,
    /** When `"immediate"`, the server emits a mid-stream `title` SSE frame so the conversation
     *  title can be revealed eagerly; `"final"` (or null) keeps the post-stream reveal. */
    val titleTiming: String? = null,
    /** Tool keys (and `"mcp"` / an MCP server name) pinned to the prompt bar by default.
     *  Parse-surface only — mobile has no pinned-tools prompt-bar concept yet (deferred). */
    val defaultPinnedTools: List<String>? = null,
    /** Shared-link sub-capabilities. `bool | { create, share, public, snapshotFiles }`.
     *  Kept as raw JSON for forward-compat, mirroring [prompts] / [agents] / [skills]. */
    val sharedLinks: JsonElement? = null,
    /** Maximum number of skills shown in the catalog. Parse-surface only (no mobile skills UI). */
    val maxCatalogSkills: Int? = null,
    // --- v0.8.8-rc2 ---
    /** Whether message feedback (thumbs up/down + reason tags) is offered. Server default is
     *  `true`; a deployment that sets it false wants the affordance gone, not disabled. */
    val feedback: Boolean = true,
    /**
     * Scheduled chats. `bool | { use, create, maxPerUser, minIntervalMinutes, requireProject,
     * projectId, … }`, kept raw for forward-compat like [prompts] / [agents] / [sharedLinks].
     *
     * **Read it through [isSchedulesEnabled], never through a generic interface-flag helper.**
     * Absent means OFF here, which is the opposite of every other flag on this class — see that
     * function's KDoc.
     */
    val schedules: JsonElement? = null,
)

/**
 * Whether scheduled chats are enabled on this server.
 *
 * **Absent is OFF.** The feature is experimental and opt-in: the server enables it only when an
 * admin says so, and the same resolution drives the write handlers and the fire path. Every other
 * object-form flag on [InterfaceConfig] defaults ON when absent, so reusing a generic helper here
 * would offer a surface whose create and run operations the backend rejects.
 *
 * The truth table, mirroring `useSideNavLinks.ts` and `getLimits` exactly:
 * absent/`null` → off · `false` → off · `true` → on · `{}` → **on** · `{ use: false }` → off.
 *
 * One deliberate departure, on input the server cannot actually produce: upstream reads anything
 * that is not null, `false` or `{ use: false }` as ON, so a string or an array would enable the
 * feature there. The config's own zod union is `boolean | object`, so neither shape survives
 * `/api/config` — and between the two possible readings of an unreadable value, OFF is the right
 * direction for a feature that is off by default.
 *
 * Note the boolean form is a RUNTIME FEATURE DISABLE, not a permission denial — a server that
 * sets `schedules: false` has turned the feature off, and telling the user they lack permission
 * would be wrong. The permission half is [com.garfiec.librechat.core.model.permissions.PermissionType.SCHEDULES]
 * and is asked separately; both must hold.
 */
fun isSchedulesEnabled(schedules: JsonElement?): Boolean {
    val element = schedules ?: return false
    (element as? JsonPrimitive)?.let { return !it.isString && it.booleanOrNull == true }
    val obj = element as? JsonObject ?: return false
    return (obj["use"] as? JsonPrimitive)?.booleanOrNull != false
}
