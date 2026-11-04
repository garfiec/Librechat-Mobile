package com.garfiec.librechat.core.model.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Machine-readable `code` values the backend puts on an error response body.
 *
 * Upstream moved from bare English messages to typed codes across the generation routes
 * (`api/server/routes/agents/index.js`, the `api/server/controllers/agents` controllers) and the MCP
 * controller (`packages/api/src/mcp/errors.ts`). A code is the only part of an error body that
 * is safe to branch on — the accompanying `message` is localized, reworded between releases and
 * sometimes replaced wholesale by a gateway.
 *
 * These are string constants rather than an enum on purpose: an unknown code must degrade to the
 * generic path, and an enum would force every call site to carry an `UNKNOWN` case that means
 * exactly what a null already means.
 */
object ServerErrorCode {
    /**
     * 409 from `POST /api/agents/chat/abort` — the run acknowledged the abort but has not yet
     * reached a stoppable point. Carries `Retry-After: 1`; the abort is expected to succeed on a
     * retry, so surfacing it as a stop failure is wrong.
     */
    const val RUN_STILL_ACTIVE = "RUN_STILL_ACTIVE"

    /**
     * 400 from the abort route when a target field is present but zero-length or over 512 chars.
     * The client should never provoke this — see `ChatAbortRequest`, which is what stopped mobile
     * sending an empty `abortKey`.
     */
    const val INVALID_ABORT_TARGET = "INVALID_ABORT_TARGET"

    /**
     * 409 from the abort route (generation protocol v1, which is what mobile speaks): the stop
     * landed but saving the partial reply failed. The server keeps the run instead of settling
     * it over an incomplete write and asks to be asked again (`Retry-After: 1`).
     */
    const val ABORT_PERSISTENCE_FAILED = "ABORT_PERSISTENCE_FAILED"

    /**
     * Generation protocol v2 only: a 200 `{ success: false, settled: true }` from the abort route
     * when the run had already ended on its own. Unreachable while mobile advertises no protocol
     * version (the route then answers v1's `{ success: true, aborted }`).
     */
    const val RUN_ALREADY_SETTLED = "RUN_ALREADY_SETTLED"

    /**
     * 400 from every `/api/agents/chat` route (v0.8.9-rc1, `validateChatMessageIds`): a turn id in
     * the body (`messageId`, `responseMessageId`, `parentMessageId`, `overrideUserMessageId`,
     * `overrideParentMessageId`) is present but not 1-256 characters of `[A-Za-z0-9_:.-]`
     * (257 for `responseMessageId` / `parentMessageId`, and for `messageId` on a compaction).
     * An empty string counts as present. The client should never provoke it: every id mobile
     * sends is a UUID, a server-issued id or `NO_PARENT`, and `ChatPayloadBuilder` drops blank ones.
     */
    const val INVALID_MESSAGE_ID = "INVALID_MESSAGE_ID"

    /**
     * 400 from a generation POST whose submission the server's content filter refused
     * (v0.8.9-rc1, `packages/api/src/middleware/contentFilter.ts`): `{ error: "content_filter_block",
     * message, source, field }`, lower-case and under `error` rather than `code`. Nothing was saved
     * or sent to the model; `message` is the server's own sentence saying what to remove.
     */
    const val CONTENT_FILTER_BLOCK = "content_filter_block"

    /** True for a [CONTENT_FILTER_BLOCK] body, whichever key carries the code. */
    fun isContentFilterBlock(body: String?): Boolean = from(body) == CONTENT_FILTER_BLOCK

    /**
     * 503 from `GET /api/agents/chat/status/:conversationId`, with `Retry-After: 1`.
     *
     * A *transient race*, not a dead run: the route re-reads the job up to three times to verify
     * the resume snapshot belongs to the same generation epoch, and answers this while that has
     * not settled or while `terminalPersistencePending` is true. Giving up here abandons a run
     * that is still alive.
     */
    const val SERVER_NOT_READY = "SERVER_NOT_READY"

    /**
     * 400 from the MCP server create/update routes once the stored OAuth client secret has been
     * bound to the authorization/token endpoint it was issued for. Changing either endpoint
     * invalidates the secret, and the write keeps failing until the user re-enters it — so this
     * is a prompt-for-input outcome, not a retry-or-report one.
     *
     * The wire value carries the `MCP_` prefix that upstream's TypeScript member name
     * (`MCPErrorCodes.OAUTH_SECRET_REENTRY_REQUIRED`) drops — see `packages/api/src/mcp/errors.ts`.
     */
    const val OAUTH_SECRET_REENTRY_REQUIRED = "MCP_OAUTH_SECRET_REENTRY_REQUIRED"

    /**
     * 400 from the MCP server update route (v0.8.8-rc4) — the API-key counterpart of
     * [OAUTH_SECRET_REENTRY_REQUIRED]. A retained admin API key is bound to the connection it was
     * entered for (`url`, `type`, `proxy`, `apiKey.authorization_type`, `apiKey.custom_header`);
     * an edit that changes any of them without re-sending `apiKey.key` is refused, and keeps being
     * refused until the key is typed again. Same wire convention: the code is under `error`.
     */
    const val API_KEY_REENTRY_REQUIRED = "MCP_API_KEY_REENTRY_REQUIRED"

    /**
     * 403 — an MCP server rejected the bearer credential (v0.8.8-rc3).
     *
     * Unlike the codes above it does not come from the `/api/mcp` routes: `handleMCPError` has no
     * branch for it. It arrives two ways. Inside a run it reaches the client as a run failure, and
     * the server's own message distinguishes the two cases — the connection was refreshed and a
     * deliberate retry may work, or the user has to sign in again. From v0.8.8 it can also fail
     * the chat start POST up front, as a coded 403 body (`getAgentErrorMetadata`, when the
     * scheduled/on-behalf-of token cannot be resolved). Never replay either automatically: the
     * tool may already have executed, and at start there is nothing a retry changes.
     */
    const val MCP_AUTHENTICATION_REJECTED = "MCP_AUTHENTICATION_REJECTED"

    /**
     * 503 — the OpenID session could not refresh an MCP bearer credential (v0.8.8-rc3). Transient
     * and retryable, which is what separates it from [MCP_AUTHENTICATION_REJECTED]. Same two
     * delivery paths: inside a run, or (v0.8.8) as the chat start POST's coded 503 body.
     */
    const val MCP_AUTHENTICATION_REFRESH_FAILED = "MCP_AUTHENTICATION_REFRESH_FAILED"

    /**
     * 409 on the chat start POST (v0.8.9-rc1, `packages/api/src/projects/turn.ts`): a project chat's
     * reference files changed while the turn was starting. `retryable: true`; nothing ran, so a new
     * send can go again.
     */
    const val PROJECT_RESOURCES_CHANGED = "PROJECT_RESOURCES_CHANGED"

    /**
     * The uncoded 404 body on the chat start POST when a project chat's context can't be resolved
     * (v0.8.9-rc1): the project is gone or not the user's. It is the server's `error` sentence, not a
     * code, so it is matched only together with the 404 at that one site.
     */
    const val PROJECT_CONTEXT_UNAVAILABLE_MESSAGE = "Conversation context unavailable"

    /**
     * The server enforces 2FA (`ENFORCE_TWO_FACTOR_AUTHENTICATION`, v0.8.9-rc1) and this password
     * account has not set it up. Arrives three ways, each carrying a setup `tempToken`: a 200 from
     * login, a 200 from refresh (no token; the session is already gone server-side), and a 403 from
     * every authenticated route. Never a ban, a soft-ban or an expired session.
     */
    const val TWO_FACTOR_ENROLLMENT_REQUIRED = "TWO_FACTOR_ENROLLMENT_REQUIRED"

    /**
     * 403 from login / verify-temp: a federated account (created by an identity provider) asked for
     * a password sign-in while 2FA is enforced. The only way in is that provider.
     */
    const val TWO_FACTOR_FEDERATED_LOGIN_BLOCKED = "TWO_FACTOR_FEDERATED_LOGIN_BLOCKED"

    /**
     * 403 from a write while `interface.termsOfService.modalAcceptance` is on and the user has not
     * accepted (v0.8.9-rc1). Lower-case on the wire. Flat `{ code }` on most routes; the remote
     * agent routes nest it as `{ error: { code } }` — read it with [isTermsNotAccepted].
     */
    const val TERMS_NOT_ACCEPTED = "terms_not_accepted"

    /**
     * 403 from a pre-auth route (login, register, password reset, 2FA, OAuth start) when the server
     * runs strict tenant isolation and the request named no tenant (v0.8.9-rc1).
     */
    const val TENANT_CONTEXT_REQUIRED = "TENANT_CONTEXT_REQUIRED"

    /** True for a [TERMS_NOT_ACCEPTED] body in either envelope: `{ code }` or `{ error: { code } }`. */
    fun isTermsNotAccepted(body: String?): Boolean {
        val obj = objectOf(body) ?: return false
        if ((obj["code"] as? JsonPrimitive)?.contentOrNull == TERMS_NOT_ACCEPTED) return true
        val nested = obj["error"] as? JsonObject ?: return false
        return (nested["code"] as? JsonPrimitive)?.contentOrNull == TERMS_NOT_ACCEPTED
    }

    /**
     * The setup `tempToken` of a [TWO_FACTOR_ENROLLMENT_REQUIRED] body, or null when [body] is any
     * other answer. Keyed on the code, never on the status: a 403 alone is just as likely the
     * user-agent soft-ban, a ban, or a proxy.
     */
    fun twoFactorEnrollmentToken(body: String?): String? {
        val obj = objectOf(body) ?: return null
        if ((obj["code"] as? JsonPrimitive)?.contentOrNull != TWO_FACTOR_ENROLLMENT_REQUIRED) return null
        return (obj["tempToken"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }
    }

    private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Reads the machine-readable code off a raw error-response body, or null when the body is
     * absent, not a JSON object, or carries no string code.
     *
     * Two keys, because the backend has two conventions: the generation routes answer
     * `{ "code": ... }`, while the MCP controller's `handleMCPError` answers
     * `{ "error": <code>, "message": ... }` (`api/server/controllers/mcp.js`). `code` wins when
     * both are present, so a body that puts a human sentence in `error` next to a real `code`
     * still reads correctly.
     *
     * Safe-casts rather than using the `.jsonPrimitive` extension, which throws on an object or
     * array value — a server that answers `{"error":{...}}` must degrade, not crash the caller.
     */
    fun from(body: String?): String? {
        val obj = objectOf(body) ?: return null
        return (obj["code"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["error"] as? JsonPrimitive)?.contentOrNull
    }

    /**
     * The `code` on a generation-route error body, WITHOUT [from]'s `error` fallback.
     *
     * On those routes the absence of a code is itself a discriminator, so the fallback cannot be
     * used there. `rejectPreliminaryParentMessageId` (`api/server/controllers/agents/request.js`)
     * answers a 409 carrying only an English sentence under `error` — the one 409 on the send route
     * that is a transient race worth retrying — while every coded 409 beside it (`RUN_REPLACED`,
     * `RESOURCE_RECOVERY_REQUIRED`, `GENERATION_PREDECESSOR_MISMATCH`, `RECOVERY_PAYLOAD_MISMATCH`)
     * must not be retried. Read through [from], that sentence comes back as a code and the
     * uncoded case becomes unreachable: nothing fails to decode, and the retry silently never fires.
     *
     * The `error` fallback stays on [from] for the MCP controller, whose bodies put the code there.
     */
    fun generationCodeOf(body: String?): String? =
        (objectOf(body)?.get("code") as? JsonPrimitive)?.contentOrNull

    /**
     * The specific messages under `errors[]` on a validation refusal, or empty.
     *
     * The MCP write routes answer a schema failure with `{ message: "Invalid configuration",
     * errors: ZodIssue[] }` (`api/server/controllers/mcp.js`). The top-level message is the same for
     * every mistake; each issue's `message` says which one — "OAuth client_secret with client_id
     * requires both authorization_url and token_url". Blank and repeated messages are dropped, and a
     * non-object issue or a non-string message is skipped rather than failing the read.
     */
    fun validationMessages(body: String?): List<String> {
        val issues = objectOf(body)?.get("errors") as? JsonArray ?: return emptyList()
        return issues
            .mapNotNull { issue -> ((issue as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }

    /**
     * The server's own sentence on a coded refusal, or null.
     *
     * The MCP controller answers every coded failure as `{ error: <code>, message }`
     * (`getMCPErrorResponse`), and for a code this client has no copy of — `MCP_DOMAIN_NOT_ALLOWED`,
     * `MCP_INSPECTION_FAILED` — the message is the only account of what went wrong. It is the
     * specific part too: "Domain "http://evil.example.org" is not allowed" names the rejected host.
     *
     * Read only when the body carries a code beside the message, so this is the server's typed
     * error envelope and not an arbitrary body. That is also why it may bypass the generic
     * display screen, which rejects any text containing a URL: that screen exists for gateway pages
     * and exception text built from request URLs (issue #287), not for a sentence the server wrote
     * about the user's own input.
     *
     * A code alone does not prove the envelope is the server's: a proxy's framework error
     * (`{"error":"Forbidden","message":…}`) has the same two keys. Pass [codePrefix] to accept only
     * one controller's codes — the MCP controller's all start with `MCP_`.
     */
    fun codedMessage(body: String?, codePrefix: String = ""): String? {
        val obj = objectOf(body) ?: return null
        val code = (obj["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: (obj["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (code.isNullOrBlank() || !code.startsWith(codePrefix)) return null
        return (obj["message"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
    }

    private fun objectOf(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        val element = runCatching { parser.parseToJsonElement(body) }.getOrNull() ?: return null
        return element as? JsonObject
    }
}
