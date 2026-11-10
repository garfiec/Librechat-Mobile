package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.common.BackendVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

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
     *  Kept as raw JSON for forward-compat, mirroring [prompts] / [agents] / [remoteAgents].
     *  Read the share default through [skillsDefaultActiveOnShare]. */
    val skills: JsonElement? = null,
    /** When true, the server exposes build metadata for an About-screen display. */
    val buildInfo: Boolean? = null,
    /** Whether the web client auto-submits a prompt passed via URL. No mobile counterpart. */
    val autoSubmitFromUrl: Boolean? = null,
    /**
     * Retention mode: `"temporary"` (server default) | `"all"` | `"ephemeral"` (v0.8.9-rc1). Only
     * `"ephemeral"` changes anything here: the server forces every new chat temporary, so the
     * temporary-chat toggle is locked on. Read it through [isForcedTemporaryRetention].
     */
    val retentionMode: String? = null,
    /**
     * Deployment theme (v0.8.9-rc1): a bundled theme name or an inline definition, applied to every
     * user. Kept raw; read it through [com.garfiec.librechat.core.model.config.theme.readServerTheme], never by hand.
     */
    val theme: JsonElement? = null,
    // --- v0.8.7 detection / gating ---
    /** Whether the context-usage gauge is enabled. Server default is `true`. Gates the
     *  chat-screen context gauge through [contextGaugeEnabled]. */
    val contextUsage: Boolean = true,
    /** Whether the context breakdown shows the cost of the branch. Server default is `false`. */
    val contextCost: Boolean = false,
    /** Display currency for that cost; absent means USD. */
    val currency: CurrencyConfig? = null,
    /** When `"immediate"`, the server emits a mid-stream `title` SSE frame so the conversation
     *  title can be revealed eagerly; `"final"` (or null) keeps the post-stream reveal. */
    val titleTiming: String? = null,
    /** Tool keys (and `"mcp"` / an MCP server name) pinned to the prompt bar by default.
     *  Parse-surface only — mobile has no pinned-tools prompt-bar concept yet (deferred). */
    val defaultPinnedTools: List<String>? = null,
    /** Shared-link sub-capabilities. `bool | { create, share, public, snapshotFiles }`.
     *  Kept as raw JSON for forward-compat, mirroring [prompts] / [agents] / [skills]. */
    val sharedLinks: JsonElement? = null,
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
    // --- v0.8.8-rc3 ---
    /**
     * Conversation trace viewer. An OBJECT — `{ enabled, showInputOutput, maxRecords, … }` — and
     * **not** a boolean-or-object union like [schedules], despite looking like one.
     *
     * Read it through [isTraceViewerEnabled], never through [isSchedulesEnabled]: the two
     * absent-cases differ, and so does `{}`.
     */
    val traceViewer: JsonElement? = null,
    // --- v0.8.8 ---
    /**
     * Pre-search cap on the web agent BUILDER's agent dropdown (1..100, default 10). Decoded only:
     * mobile has no builder-side picker, and the chat model selector is deliberately NOT capped by
     * it (web's is not either).
     *
     * Raw JSON rather than `Int?`: upstream notes unparsed yaml and admin overrides reach the
     * client unvalidated, and a non-integer here must not fail the whole `/api/config` decode.
     */
    val agentSelectorLimit: JsonElement? = null,
    /**
     * MCP server sub-capabilities, `{ placeholder, use, create, share, public, configureObo,
     * trustCheckbox }`. Raw like [prompts] / [agents]; read the trust text through [mcpTrustCheckbox].
     */
    val mcpServers: JsonElement? = null,
    // --- v0.8.9-rc1 ---
    /**
     * What heads the new-chat screen when an agent or a branded spec is selected: its name
     * (`"entity"`, the default) or the time-of-day greeting (`"greeting"`). Read it through
     * [landingHeadingIsGreeting].
     */
    val landingHeading: String? = null,
    /**
     * Whether web's welcome screen carries the generic disclaimer and policy links under the
     * composer. Decoded only: the mobile new-chat screen has no such footer to hide.
     */
    val landingFooter: Boolean? = null,
    /**
     * Set false during a rolling upgrade, until every API replica supports conversation title
     * ownership. Server default `true`. Read together with
     * [StartupConfig.conversationTitleOwnershipVersion] through [supportsConversationTitleOwnership].
     */
    val runningChatRename: Boolean? = null,
    /** Client confirmation window for a steer arm (ms). Raw; read through [steerArmConfirmationTimeoutMs]. */
    val steerArmConfirmationTimeoutMs: JsonElement? = null,
    /**
     * How long a transport-ambiguous queued turn stays reconcilable (ms). Raw like
     * [agentSelectorLimit]: admin overrides reach the client unvalidated. Read through
     * [queuedTurnReconciliationTimeoutMs].
     */
    val queuedTurnReconciliationTimeoutMs: JsonElement? = null,
    /** How long a queued send may hold its pane (ms). Decoded only: mobile holds no such lock. */
    val queuedSendLockTimeoutMs: JsonElement? = null,
    /** What the deployment lets readers turn on for replies. Read through [replyNotificationsAllowed]. */
    val replyNotifications: ReplyNotificationsConfig? = null,
)

/**
 * `interface.replyNotifications`. Only the two gates mobile has a counterpart for are decoded;
 * `tabBadge` and the poll fields drive web's tab title and away poll.
 */
@Serializable
data class ReplyNotificationsConfig(
    /** Whether readers may turn on reply notifications (web: desktop notifications). */
    val desktop: Boolean? = null,
    /** Whether a reply notification may make a sound. */
    val sound: Boolean? = null,
)

/**
 * Whether the deployment allows reply notifications. A missing field is allowed: web reads each gate
 * as `!== false`, so a server that predates the setting behaves as it did before it existed.
 * MIRRORED from web's `useReplyNotificationCapabilities` (scripts/mirrors.json).
 */
val InterfaceConfig?.replyNotificationsAllowed: Boolean get() = this?.replyNotifications?.desktop != false

/** Whether a reply notification may play a sound; same reading as [replyNotificationsAllowed]. */
val InterfaceConfig?.replyNotificationSoundAllowed: Boolean get() = this?.replyNotifications?.sound != false

/** Upstream `DEFAULT_QUEUED_TURN_RECONCILIATION_TIMEOUT_MS` (`packages/data-provider/src/config.ts`). */
const val DEFAULT_QUEUED_TURN_RECONCILIATION_TIMEOUT_MS: Long = 60_000

/** Upstream's schema ceiling on the queue/steer timeouts: a positive int no larger than 2^31 - 1. */
private const val MAX_INTERFACE_TIMEOUT_MS: Long = 2_147_483_647

/**
 * The effective `interface.queuedTurnReconciliationTimeoutMs`: a positive whole number of
 * milliseconds up to upstream's ceiling, else [DEFAULT_QUEUED_TURN_RECONCILIATION_TIMEOUT_MS]
 * (absent, `null`, a string, a fraction or out of range all fall back, as the schema default would).
 */
fun InterfaceConfig?.queuedTurnReconciliationTimeoutMs(): Long =
    interfaceTimeoutMs(this?.queuedTurnReconciliationTimeoutMs, DEFAULT_QUEUED_TURN_RECONCILIATION_TIMEOUT_MS)

/** Upstream `DEFAULT_STEER_ARM_CONFIRMATION_TIMEOUT_MS` (`packages/data-provider/src/config.ts`). */
const val DEFAULT_STEER_ARM_CONFIRMATION_TIMEOUT_MS: Long = 10_000

/**
 * The effective `interface.steerArmConfirmationTimeoutMs`: how long the client waits for a
 * `/chat/steer/arm` reply before saying it couldn't confirm the interrupt. Parsed exactly like
 * [queuedTurnReconciliationTimeoutMs], else [DEFAULT_STEER_ARM_CONFIRMATION_TIMEOUT_MS].
 */
fun InterfaceConfig?.steerArmConfirmationTimeoutMs(): Long =
    interfaceTimeoutMs(this?.steerArmConfirmationTimeoutMs, DEFAULT_STEER_ARM_CONFIRMATION_TIMEOUT_MS)

private fun interfaceTimeoutMs(raw: JsonElement?, default: Long): Long {
    val primitive = raw as? JsonPrimitive ?: return default
    if (primitive.isString) return default
    val value = primitive.longOrNull ?: return default
    return value.takeIf { it in 1..MAX_INTERFACE_TIMEOUT_MS } ?: default
}

/** Upstream `isForcedTemporaryRetention`: only `retentionMode: "ephemeral"` forces every chat temporary. */
fun isForcedTemporaryRetention(retentionMode: String?): Boolean = retentionMode == RETENTION_MODE_EPHEMERAL

/** Upstream `RetentionMode.EPHEMERAL`. */
const val RETENTION_MODE_EPHEMERAL: String = "ephemeral"

/** `interface.landingHeading == "greeting"`: the time-of-day greeting heads the new chat whatever is selected. */
fun InterfaceConfig?.landingHeadingIsGreeting(): Boolean = this?.landingHeading == "greeting"

/** The release that ships the gauge's `on_context_usage` SSE and `/api/endpoints/token-config`. */
private const val CONTEXT_GAUGE_MIN_VERSION = "0.8.7-rc1"

/**
 * Chat's context gauge: a server from v0.8.7-rc1 (an older or unknown [backendVersion] fails closed)
 * whose `interface.contextUsage` isn't off.
 */
fun InterfaceConfig?.contextGaugeEnabled(backendVersion: String?): Boolean =
    backendVersion != null &&
        BackendVersion.isCompatibleOrNewer(backendVersion, CONTEXT_GAUGE_MIN_VERSION) &&
        (this?.contextUsage ?: true)

/** Web's presets menu (`Header.tsx`): `interface.presets && interface.modelSelect`, each on when absent. */
fun InterfaceConfig?.presetsMenuEnabled(): Boolean = (this?.presets ?: true) && (this?.modelSelect ?: true)

/** Upstream `CONVERSATION_TITLE_OWNERSHIP_VERSION`. */
const val CONVERSATION_TITLE_OWNERSHIP_VERSION: Int = 1

/**
 * Whether a skill shared with the user (not owned, not a deployment skill) is active for them until
 * they toggle it: `interface.skills.defaultActiveOnShare`.
 *
 * Mirrors upstream `resolveDefaultActiveOnShare` (`packages/api/src/skills/skillStates.ts`): only the
 * object form can carry the key, and only an explicit `true` turns it on. The boolean form of
 * `interface.skills`, an absent section and any other value all read as `false`.
 */
fun skillsDefaultActiveOnShare(skills: JsonElement?): Boolean {
    val obj = skills as? JsonObject ?: return false
    return (obj["defaultActiveOnShare"] as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == true } == true
}

/**
 * Whether the conversation trace viewer is enabled on this server.
 *
 * **`{}` is OFF here**, which is where this differs from [isSchedulesEnabled] — the shapes look
 * alike and the rules are not. Upstream's `resolveTraceViewerConfig` reads `config?.enabled ===
 * true`, so only an explicit `enabled: true` turns it on:
 * absent → off · `{}` → **off** · `{ enabled: false }` → off · `{ enabled: true }` → on.
 *
 * A bare `traceViewer: true` is off as well. It is not schema-valid (the section is
 * `z.object({…}).optional()` with no boolean arm), and upstream reading `.enabled` off a boolean
 * gets `undefined` — so the two agree, and a deployment that wrote the boolean form by analogy
 * with `schedules` gets the same nothing from both clients.
 *
 * The other fields — `showInputOutput` and the four numeric budgets — are enforced entirely
 * server-side: content is withheld by `contentAvailable` on the record detail, and the budgets
 * bound what the server reads and how often it may be asked. Nothing here needs them, so nothing
 * here mirrors them.
 */
fun isTraceViewerEnabled(traceViewer: JsonElement?): Boolean {
    val obj = traceViewer as? JsonObject ?: return false
    return (obj["enabled"] as? JsonPrimitive)?.booleanOrNull == true
}

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

/**
 * Display currency for the context cost (`interface.currency`). [rate] is a static USD→local
 * multiplier; [code] an ISO-4217 code.
 */
@Serializable
data class CurrencyConfig(
    val code: String,
    val rate: Double = 1.0,
)
