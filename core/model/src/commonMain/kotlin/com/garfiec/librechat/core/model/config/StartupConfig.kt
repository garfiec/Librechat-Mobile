package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class StartupConfig(
    val appTitle: String = "LibreChat",
    val emailLoginEnabled: Boolean = true,
    val registrationEnabled: Boolean = false,
    val socialLoginEnabled: Boolean = false,
    val socialLogins: List<String>? = null,
    val googleLoginEnabled: Boolean = false,
    val githubLoginEnabled: Boolean = false,
    val discordLoginEnabled: Boolean = false,
    val facebookLoginEnabled: Boolean = false,
    val appleLoginEnabled: Boolean = false,
    val openidLoginEnabled: Boolean = false,
    val openidLabel: String? = null,
    val openidImageUrl: String? = null,
    val openidAutoRedirect: Boolean = false,
    val samlLoginEnabled: Boolean = false,
    val samlLabel: String? = null,
    val samlImageUrl: String? = null,
    val emailEnabled: Boolean = true,
    val passwordResetEnabled: Boolean = false,
    val publicSharedLinksEnabled: Boolean = false,
    val sharedLinksEnabled: Boolean = false,
    val serverDomain: String = "",
    val helpAndFaqURL: String? = null,
    val minPasswordLength: Int? = null,
    val showBirthdayIcon: Boolean = false,
    val modelSpecs: ModelSpecs? = null,
    @SerialName("interface") val interfaceConfig: InterfaceConfig? = null,
    val turnstile: TurnstileConfig? = null,
    val balance: BalanceConfig? = null,
    val analyticsGtmId: String? = null,
    val allowAccountDeletion: Boolean = true,
    val bundlerURL: String? = null,
    val staticBundlerURL: String? = null,
    val sharePointFilePickerEnabled: Boolean? = null,
    val sharePointBaseUrl: String? = null,
    val sharePointPickerGraphScope: String? = null,
    val sharePointPickerSharePointScope: String? = null,
    val openidReuseTokens: Boolean? = null,
    val conversationImportMaxFileSize: Long? = null,
    val webSearch: JsonObject? = null,
    val ldap: LdapConfig? = null,
    /** Backend version, if the server includes it in the config response (not yet standard). */
    val version: String? = null,
    // --- v0.8.6 detection (parse-surface only) ---
    /** Real-user-monitoring config (server-side telemetry). Opaque to mobile; kept for parse coverage. */
    val rum: JsonObject? = null,
    /** CloudFront signed-cookie refresh config. Mobile downloads directly, so opaque/unused. */
    val cloudFront: JsonObject? = null,
    /** Server build metadata (commit/branch/buildDate), gated by `interface.buildInfo`. */
    val buildInfo: BuildInfo? = null,
    // --- v0.8.8 detection (parse-surface only) ---
    /**
     * The operator's admin panel URL, emitted **only** when `ADMIN_PANEL_URL` is configured AND
     * the caller has the ACCESS_ADMIN capability — it is stripped for unauthenticated requests
     * and for non-admins, and upstream's tests assert that.
     *
     * That makes its **presence** the server's own admin signal, which is the cheapest way for a
     * client to decide whether to offer an admin entry point at all. Two consequences: absence is
     * the normal case and must never be treated as an error, and the value **must not be cached
     * across accounts** — it is a per-caller answer, not a property of the deployment.
     *
     * No mobile admin UI is in scope; this is decode surface.
     */
    val adminPanelURL: String? = null,
    /** Langfuse observability fan-out. Always present; admin-facing, opaque to mobile. */
    val langfuseFanoutEnabled: Boolean? = null,
    /** Which roles may manage Langfuse connections. Always present; admin-facing, opaque here. */
    val langfuseConnectionAccess: JsonElement? = null,
    // --- v0.8.7 detection (parse-surface only) ---
    /** Whether shared links may include snapshot file copies. Mirrors `interface.sharedLinks.snapshotFiles`
     *  at startup level. Only `true` shows the share sheet's "Include files" switch, as on web. */
    val sharedLinksSnapshotFilesEnabled: Boolean? = null,
    // --- 0.8.8 line detection (parse-surface only) ---
    /**
     * Whether the server offers the SSE-heartbeat file-upload lifecycle (`FILE_UPLOAD_SSE_ENABLED`).
     * Detection-only: mobile deliberately stays on the multipart/JSON upload path regardless, so
     * nothing branches on this yet. Absent (null) on older backends; server default is off.
     */
    val fileUploadSseEnabled: Boolean? = null,
    // --- v0.8.8-rc3 ---
    /**
     * Per-endpoint `dropParams`: parameters the server strips from the provider request. Kept as
     * raw JSON because the value is a union — a list of names for most endpoints, a per-model
     * lookup for `azureOpenAI`. Read through [EndpointDropParams.resolve].
     *
     * Stripped for unauthenticated callers, so absent does not mean "nothing is dropped" on a
     * pre-login config read.
     */
    val endpointsDropParamsMap: Map<String, JsonElement>? = null,
    /**
     * Whether manual context compaction is offered (`appConfig?.summarization?.enabled !== false`,
     * so it is `true` unless an admin turned summarization off).
     *
     * Null on any server that predates it, which is the gate: an announced capability, so nothing
     * stacks a version check on top. Absent means the send would carry a `compact` flag the server
     * ignores, producing an empty turn — so absent is withheld, not defaulted true.
     */
    val compactionEnabled: Boolean? = null,
    /**
     * `ENFORCE_TWO_FACTOR_AUTHENTICATION` (v0.8.9-rc1). Every password (local/LDAP) account must have
     * 2FA, and the server refuses to disable it for them. It hides Disable 2FA; it does not decide
     * whether setup is required, which the server says per response
     * (`TWO_FACTOR_ENROLLMENT_REQUIRED`).
     */
    val twoFactorAuthenticationRequired: Boolean? = null,
    /**
     * Conversation title ownership capability (v0.8.9-rc1): `1` when this replica tracks who set a
     * title (`titleSetByUser` / `titleRevision`). Absent on older servers and older replicas.
     */
    val conversationTitleOwnershipVersion: Int? = null,
    /**
     * What one chat-list filter request may name (v0.8.9-rc1, post-login only). Null on older
     * servers; [ConversationListLimits.DEFAULT] stands in, as upstream's schema defaults do.
     */
    val conversationListLimits: ConversationListLimits? = null,
    /**
     * `ALLOW_PASSKEY_LOGIN` with at least one origin configured (v0.8.9-rc1). Every
     * `/api/auth/passkey` route answers 404 while it is off, so absent is off.
     */
    val passkeyLoginEnabled: Boolean = false,
    /**
     * `passkeys.perUserMax` / `MAX_PASSKEYS_PER_USER` (v0.8.9-rc1, post-login only). Null on older
     * servers and before sign-in; the server's 409 is the real guard.
     */
    val maxPasskeysPerUser: Int? = null,
    /**
     * Whether a local account may change its email (v0.8.9-rc1, `emailChange.enabled` /
     * `ALLOW_EMAIL_CHANGE`). Web reads `=== true`; older servers don't send it, so absent is off.
     * Only meaningful together with [emailEnabled]: the change needs a mail transport.
     */
    val allowEmailChange: Boolean = false,
    /**
     * Whether the server has a RAG API (`RAG_API_URL` set; v0.8.9-rc1, post-login only). Project
     * reference files are searched through it, so web hides a project's Files section without it.
     * Null before sign-in and on older servers.
     */
    val ragEnabled: Boolean? = null,
    /** The `projects` limits block (v0.8.9-rc1, post-login only). Null stands for upstream's defaults. */
    val projects: ChatProjectsConfig? = null,
    /**
     * Attached-machine capability versions. Each is compared with `==`, as web does
     * (`CodeCapabilityVersions`): a version this client does not know is unsupported. The decision
     * version is an env var admins may leave unset, and without it a client must never send a
     * selection-less ("No workspace") decision. Inheritance is v0.8.9-rc1; the rest are 0.8.8.
     */
    val codeEnvironmentDecisionVersion: Int? = null,
    val codeEnvironmentMoveVersion: Int? = null,
    val codeEnvironmentTransitionVersion: Int? = null,
    val codeWorkspaceRecoveryVersion: Int? = null,
    val codeWorkspaceInheritanceVersion: Int? = null,
    /**
     * The MCP Apps policy (v0.8.9-rc1, post-login only). Raw on purpose: read it through
     * [mcpAppsPolicy], which treats a missing or malformed policy as Apps off, as web does.
     */
    val mcpApps: JsonElement? = null,
    /**
     * Conversation pull requests (v0.8.9-rc1, post-login only): true when the server looks up the pull
     * request a code chat opened. The three below come only with it.
     */
    val pullRequestsEnabled: Boolean? = null,
    /** The batch lookup route's version; the drawer asks in batches only when it is 1. */
    val pullRequestsBatchVersion: Int? = null,
    /** How many lookups the server runs at once (default 4); bounds the batch chunks in flight. */
    val pullRequestsMaxConcurrentLookups: Int? = null,
    /** The provider users may connect their own account to. Decoded only: mobile has no connect setting. */
    val pullRequestsConnect: String? = null,
) {
    /** The resolved MCP Apps policy; [McpAppsPolicy.enabled] is the gate for every App row. */
    val mcpAppsPolicy: McpAppsPolicy
        get() = McpAppsPolicy.resolve(mcpApps)

    /**
     * Upstream `supportsConversationTitleOwnership`: the capability is announced AND
     * `interface.runningChatRename` is not off. Upstream reads `=== true` off a parsed config whose
     * schema defaults the flag to true, so an absent flag counts as on here.
     */
    val supportsConversationTitleOwnership: Boolean
        get() = conversationTitleOwnershipVersion == CONVERSATION_TITLE_OWNERSHIP_VERSION &&
            interfaceConfig?.runningChatRename != false
}
