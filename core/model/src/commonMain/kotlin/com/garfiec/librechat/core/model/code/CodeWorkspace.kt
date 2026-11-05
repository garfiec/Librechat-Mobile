package com.garfiec.librechat.core.model.code

import kotlinx.serialization.Serializable

/**
 * Wire shapes for attached code machines (a user's own computer or VM running the
 * `librechat-code` worker) and the workspaces a chat runs on there. MIRRORED from upstream
 * `packages/data-provider/src/code/workspace.ts` and `types.ts` (`TPublicCodeEnvironment`,
 * `TCodeEnvironmentStatusResponse`); see `scripts/mirrors.json`.
 */

/** How a conversation runs its coding agents: on their attached machine, or without one. */
object CodeEnvironmentMode {
    const val ATTACHED = "attached"
    const val WITHOUT_ATTACHED = "without_attached"

    /** The wire value when it is one this client knows, else null (an unknown mode decides nothing). */
    fun of(raw: String?): String? = raw?.takeIf { it == ATTACHED || it == WITHOUT_ATTACHED }
}

/** `checkout` values. Absent means Auto: the worker picks how to check out. */
object CodeWorkspaceCheckout {
    /** The machine's registered checkout, shared with other chats. */
    const val SOURCE = "source"

    /** The chat's own Git worktree. Only where the workspace advertises [INSTANCE_GIT_WORKTREE]. */
    const val ISOLATED = "isolated"

    const val INSTANCE_GIT_WORKTREE = "git_worktree"
    const val SCOPE_GIT_LINKED_WORKTREE = "git_linked_worktree"
}

/**
 * One exact workspace per attached machine the conversation uses. [agentIds] records which saved
 * agents a per-chat machine choice belongs to; absent on a pick no agent owns.
 */
@Serializable
data class CodeWorkspaceSelection(
    val environmentId: String,
    val workspaceId: String,
    val checkout: String? = null,
    val agentIds: List<String>? = null,
)

/** A machine as `GET /api/endpoints` serves it under `agents.statefulCodeSessions.environments`. */
@Serializable
data class PublicCodeEnvironment(
    val id: String,
    val name: String? = null,
    /** `managed` or `attached`. Raw so a new type never fails the whole endpoints decode. */
    val type: String? = null,
    val default: Boolean? = null,
    val configSchema: CodeEnvironmentConfigSchema? = null,
    /** The user's own permission choices for this machine, within [configSchema]'s bounds. */
    val settings: CodeEnvironmentUserSettings? = null,
) {
    val isAttached: Boolean get() = type == TYPE_ATTACHED
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id

    companion object {
        const val TYPE_ATTACHED = "attached"
    }
}

@Serializable
data class CodeEnvironmentConfigSchema(
    val workspaces: CodeEnvironmentWorkspacesSchema? = null,
    /** Which file-change and command decisions the user may choose; absent = not configurable. */
    val permissions: CodeEnvironmentPermissionsSchema? = null,
)

/** `allow`, `ask` or `deny`. MIRRORED from upstream `codeEnvironmentPermissionDecisionSchema`. */
object CodePermissionDecision {
    const val ALLOW = "allow"
    const val ASK = "ask"
    const val DENY = "deny"
}

/** One configurable permission: the decisions offered, and the one used when the user picked none. */
@Serializable
data class CodeEnvironmentPermissionField(
    val allowed: List<String> = emptyList(),
    /** Upstream defaults it to `ask`. */
    val default: String? = null,
) {
    /** The user's [configured] decision when it is offered, else the default. */
    fun effective(configured: String?): String =
        configured?.takeIf { it in allowed } ?: default ?: CodePermissionDecision.ASK
}

@Serializable
data class CodeEnvironmentPermissionsSchema(
    val fileWrite: CodeEnvironmentPermissionField? = null,
    val commandExecution: CodeEnvironmentPermissionField? = null,
)

@Serializable
data class CodeEnvironmentPermissionSettings(
    val fileWrite: String? = null,
    val commandExecution: String? = null,
)

/** `settings` on a machine; also the body of `PATCH /api/code-environments/:id/settings`. */
@Serializable
data class CodeEnvironmentUserSettings(
    val permissions: CodeEnvironmentPermissionSettings? = null,
)

@Serializable
data class CodeEnvironmentWorkspacesSchema(
    /** Only true lets the user pick a checkout mode; upstream defaults it to false. */
    val allowCheckoutSelection: Boolean? = null,
    /** On unless false. */
    val linkedWorktrees: Boolean? = null,
)

/** `agents.statefulCodeSessions` on `GET /api/endpoints`. Already scoped to the caller. */
@Serializable
data class StatefulCodeSessionsConfig(
    val environments: List<PublicCodeEnvironment>? = null,
    /** Present (and true) only when per-chat machine choice is on. */
    val allowEnvironmentSelection: Boolean? = null,
    val maxEnvironmentChoices: Int? = null,
    /** Tool approvals are on (`toolApproval.enabled !== false`); the approval-mode picker needs it true. */
    val approvalsEnabled: Boolean? = null,
    /** The approval modes the endpoint policy lets a client offer ([CodeApprovalMode]). */
    val approvalModes: List<String>? = null,
    /** Workspace scopes this deployment allows ([StatefulCodeScope]); null allows all three. */
    val allowedEnvironments: List<String>? = null,
)

/**
 * Who shares an agent's stateful workspace (`stateful_code_environment`). MIRRORED from upstream
 * `packages/data-provider/src/stateful-code.ts`; see `scripts/mirrors.json`.
 */
object StatefulCodeScope {
    const val USER = "user"
    const val AGENT_USER = "agent-user"
    const val CONVERSATION = "conversation"

    /** Upstream's fixed UI order. */
    val ALL: List<String> = listOf(USER, AGENT_USER, CONVERSATION)

    /** `resolveAllowedStatefulCodeEnvironments`: the deployment allowlist in UI order; null allows all. */
    fun allowed(configured: List<String>?): List<String> = if (configured == null) ALL else ALL.filter { it in configured }

    /** `resolveStatefulCodeEnvironment`: an allowed preference, else the first allowed scope. */
    fun resolve(preferred: String?, configured: List<String>?): String? {
        val allowed = allowed(configured)
        return preferred?.takeIf { it in allowed } ?: allowed.firstOrNull()
    }
}

/** Public, path-free description of one root an attached worker registered. */
@Serializable
data class CodeWorkspaceDescriptor(
    val id: String,
    val name: String? = null,
    val workspaceInstances: List<String>? = null,
    val workspaceScopes: List<String>? = null,
    val environment: CodeWorkspaceGitInfo? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
    val offersWorktree: Boolean get() = workspaceInstances?.contains(CodeWorkspaceCheckout.INSTANCE_GIT_WORKTREE) == true
}

@Serializable
data class CodeWorkspaceGitInfo(
    val repo: String? = null,
    val ref: String? = null,
)

/** `GET /api/code-environments/:id/status`. [workspaces] missing means the worker predates them. */
@Serializable
data class CodeEnvironmentStatus(
    val environmentId: String,
    /** `offline`, `starting` or `ready`. */
    val status: String? = null,
    val workspaces: List<CodeWorkspaceDescriptor>? = null,
    /** The worker's sandbox profile, shown with [runtimes] in Settings. */
    val sandboxProfile: String? = null,
    val runtimes: List<String>? = null,
) {
    val isReady: Boolean get() = status == STATUS_READY

    companion object {
        const val STATUS_READY = "ready"
        const val STATUS_STARTING = "starting"
        const val STATUS_OFFLINE = "offline"
    }
}

/**
 * The capability versions `/api/config` advertises. Each is matched EXACTLY, as web does: an
 * unknown higher number counts as unsupported.
 */
object CodeCapabilityVersions {
    const val DECISION = 1
    const val MOVE = 1
    const val TRANSITION = 2
    const val RECOVERY = 1
    const val INHERITANCE = 1
}

/** The six `reason`s a `code_workspace_unavailable` rejection carries (`errors.ts`). */
enum class CodeWorkspaceUnavailableReason(val wire: String) {
    REQUIRED("required"),
    INVALID("invalid"),
    WORKER_UNAVAILABLE("worker_unavailable"),
    UNSUPPORTED("unsupported"),
    MISSING("missing"),
    LOCKED("locked"),
    ;

    /** The workspace itself needs attention: refresh the machines and mark the chip. */
    val needsWorkspaceAttention: Boolean
        get() = this == REQUIRED || this == MISSING || this == WORKER_UNAVAILABLE

    companion object {
        fun of(raw: String?): CodeWorkspaceUnavailableReason? = entries.firstOrNull { it.wire == raw }
    }
}

/**
 * `PATCH /api/code-environments/conversations/:id/decision`. [from] is exactly what the chat
 * stores (empty for a chat running without a workspace); an empty [to] continues without one.
 */
@Serializable
data class CodeEnvironmentMoveRequest(
    val from: List<CodeWorkspaceSelection>,
    val to: List<CodeWorkspaceSelection>,
)

/** The decision the server now holds. [codeWorkspaces] is absent once the chat runs without one. */
@Serializable
data class CodeEnvironmentMoveResponse(
    val conversationId: String? = null,
    val codeEnvironmentMode: String? = null,
    val codeWorkspaces: List<CodeWorkspaceSelection>? = null,
)

/**
 * The decision a send carries; both null sends neither field. [approvalMode] is the turn's code
 * approval mode (`codeApprovalMode`), sent beside them; null sends none.
 */
data class CodeWorkspaceSubmission(
    val mode: String? = null,
    val workspaces: List<CodeWorkspaceSelection>? = null,
    val approvalMode: String? = null,
) {
    companion object {
        val NONE = CodeWorkspaceSubmission()
    }
}
