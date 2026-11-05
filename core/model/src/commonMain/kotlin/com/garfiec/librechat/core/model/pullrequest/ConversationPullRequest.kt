package com.garfiec.librechat.core.model.pullrequest

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.model.config.StartupConfig
import kotlinx.serialization.Serializable

/**
 * The pull request a conversation's attached code workspace opened (v0.8.9-rc1,
 * `TConversationPullRequest`). The enumerated fields stay raw strings so a value a newer server adds
 * never fails the decode; read them through [presentPullRequest].
 */
@Serializable
data class ConversationPullRequest(
    val number: Int,
    val title: String = "",
    /** The pull request's page on its provider (a merge request on GitLab). */
    val url: String = "",
    /** `github` | `gitlab` | `bitbucket`; absent from a server that predates providers (GitHub). */
    val provider: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    /** `open` | `closed` | `merged`. */
    val state: String = "open",
    val isDraft: Boolean = false,
    /** `clean` | `conflicting` | `unknown` (still computing, and for closed or merged). */
    val mergeable: String = "unknown",
    /** Rollup of the head commit's checks: `passing` | `failing` | `running` | `none`. */
    val checks: String = "none",
)

/** `GET /api/convos/:id/pull-request`. A null [pullRequest] means the chat has none, not an error. */
@Serializable
data class ConversationPullRequestResponse(val pullRequest: ConversationPullRequest? = null)

/** `POST /api/convos/pull-requests`. A bare body, not `arg`-wrapped; at most [PULL_REQUEST_BATCH_MAX] ids. */
@Serializable
data class ConversationPullRequestsRequest(val conversationIds: List<String>)

@Serializable
data class ConversationPullRequestsResponse(val results: List<ConversationPullRequestsEntry> = emptyList())

/** One conversation's answer: its pull request (null when none), or a stable failure [error] code. */
@Serializable
data class ConversationPullRequestsEntry(
    val conversationId: String,
    val pullRequest: ConversationPullRequest? = null,
    val error: PullRequestError? = null,
)

/** A lookup failure. [code] is one of [PullRequestErrorCodes]; it never carries provider text. */
@Serializable
data class PullRequestError(val code: String? = null)

/**
 * MIRRORED from upstream `packages/data-provider/src/types/pullRequest.ts` (registered in
 * `scripts/mirrors.json`): the most ids one batch lookup may name.
 */
const val PULL_REQUEST_BATCH_MAX = 50

/** MIRRORED from upstream `PULL_REQUEST_BATCH_VERSION`: the batch route version a client may ask. */
const val PULL_REQUEST_BATCH_VERSION = 1

/** Upstream's default for `pullRequestsMaxConcurrentLookups` when the config omits it. */
const val PULL_REQUEST_DEFAULT_CONCURRENCY = 4

/** MIRRORED from upstream `packages/api/src/pulls/types.ts`. Logged only; all read the same to the user. */
object PullRequestErrorCodes {
    const val NOT_CONFIGURED = "NOT_CONFIGURED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val UPSTREAM_ERROR = "UPSTREAM_ERROR"
    const val CREDENTIAL_REJECTED = "CREDENTIAL_REJECTED"
}

/**
 * MIRRORED from upstream `client/src/components/Chat/PullRequest/code.ts` (`isCodeConversation`): only
 * a chat that ran against an attached code workspace can have a pull request.
 */
fun isCodeConversation(codeEnvironmentMode: String?, codeWorkspaceCount: Int): Boolean =
    codeEnvironmentMode == "attached" || codeWorkspaceCount > 0

private const val SETTLED_REFRESH_MS = 60_000L
private const val ACTIVE_REFRESH_MS = 20_000L

/**
 * MIRRORED from upstream `pullRequestRefetchInterval` (`client/src/data-provider/PullRequest/queries.ts`):
 * how long to wait before asking again, or null to stop. Fast while checks run or mergeability is
 * still being computed; a closed or merged pull request whose checks finished stops; a chat with no
 * pull request is asked again slowly.
 */
fun pullRequestRefetchIntervalMs(pullRequest: ConversationPullRequest?): Long? {
    val pr = pullRequest ?: return SETTLED_REFRESH_MS
    if (pr.checks == "running") return ACTIVE_REFRESH_MS
    if (pr.state != "open") return null
    return if (pr.mergeable == "unknown") ACTIVE_REFRESH_MS else SETTLED_REFRESH_MS
}

/** The drawer re-asks a row whose answer is older than this (upstream's row `staleTime`). */
const val PULL_REQUEST_ROW_STALE_MS = SETTLED_REFRESH_MS

/**
 * The drawer's pull-to-refresh re-asks a row only when its answer is older than this, so a burst of
 * pulls sends one batch. Mobile-only: upstream's sidebar has no pull-to-refresh.
 */
const val PULL_REQUEST_ROW_FORCE_MS = 15_000L

/** The chat screen re-asks on return when its answer is older than this (upstream's `staleTime`). */
const val PULL_REQUEST_CHAT_STALE_MS = 15_000L

/**
 * `GET /api/pull-requests/oauth/status` (upstream `TPullRequestConnection`): whether the user has
 * connected their own account for pull request lookups. [connected] can be true while [configured]
 * is false: a grant whose app the server no longer has, which can still be removed.
 */
@Serializable
data class PullRequestConnection(
    val configured: Boolean = false,
    /** `github` | `gitlab` | `bitbucket`; absent while nothing is configured. */
    val provider: String? = null,
    val connected: Boolean = false,
    /** The sign-in that produced the grant, when the server names it. */
    val flowId: String? = null,
)

/**
 * `GET /api/pull-requests/oauth/connect` (upstream `TPullRequestConnectStart`): the provider page to
 * open. The same response sets the `oauth_csrf` and `oauth_session` cookies the callback checks.
 */
@Serializable
data class PullRequestConnectStart(
    val url: String,
    val expiresInSeconds: Int? = null,
    val flowId: String? = null,
)

/**
 * MIRRORED from upstream `hasPullRequestConnect` (`client/src/components/Nav/Settings/context.tsx`):
 * the account row shows while a grant exists (so it can be removed), or while the deployment offers
 * an account to connect and the status hasn't said its app is gone.
 */
fun showsPullRequestAccount(connectProvider: String?, connection: PullRequestConnection?): Boolean =
    connection?.connected == true || (connectProvider != null && connection?.configured != false)

/**
 * Whether asking for the connection can find anything (upstream `isConnectionKnowable`): pull
 * requests on, or an app to connect. A grant made before the app was turned off must still be found.
 */
fun pullRequestConnectionKnowable(config: StartupConfig?, backendVersion: String?): Boolean =
    (config?.pullRequestsEnabled == true || config?.pullRequestsConnect != null) &&
        backendVersion != null &&
        BackendVersion.isCompatibleOrNewer(backendVersion, PULL_REQUESTS_MIN_VERSION)
