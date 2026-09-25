package com.garfiec.librechat.core.model.error

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The typed `type` values the backend puts on a stream-error payload — upstream's `ErrorTypes`
 * (`packages/data-provider/src/config.ts`).
 *
 * The server used to swallow several of these and let the run continue on stale state; it now
 * raises them as typed errors and propagates them. Without a mapping a client renders the raw
 * JSON payload as the error text, which is how `{"type":"resource_recovery_required", …}` ends up
 * on screen where a sentence telling the user to reattach their files belongs.
 *
 * Only the codes with something ACTIONABLE to say are listed. That is the point of the type: an
 * unrecognized code must fall through to the generic message rather than being surfaced raw, so
 * adding a case is how a code stops being generic — never a requirement for one to be safe.
 *
 * The user-provided-key codes deliberately are NOT here. They already route through
 * [parseUserKeyError] to a snackbar with a Settings action, and duplicating them would surface the
 * same failure twice.
 */
enum class StreamErrorType(val wire: String) {
    /**
     * Required CodeAPI files could not be restored before the model ran.
     *
     * Recoverable only by the user: the files behind the run are gone, so retrying the same turn
     * fails identically until they are attached again.
     */
    RESOURCE_RECOVERY_REQUIRED("resource_recovery_required"),

    /** The model the turn asked for is not available on this deployment any more. */
    MISSING_MODEL("missing_model"),

    /**
     * The provider rejected the model as unknown.
     *
     * A typed `ErrorTypes` member from v0.8.8-rc2 onward. Servers at or below rc1 have no code for
     * it and instead embed a LangChain documentation URL in provider prose, which
     * [Companion.MODEL_NOT_FOUND_PATTERN] still matches.
     */
    MODEL_NOT_FOUND("model_not_found"),

    /** The provider throttled the request for exceeding a rate or spend allowance. */
    MODEL_RATE_LIMIT("model_rate_limit"),

    /** The models configuration has not loaded, so no model can be resolved yet. */
    MODELS_NOT_LOADED("models_not_loaded"),

    /** This endpoint's model list has not loaded. */
    ENDPOINT_MODELS_NOT_LOADED("endpoint_models_not_loaded"),

    /** The admin excluded this agent's provider. */
    INVALID_AGENT_PROVIDER("invalid_agent_provider"),

    /** The model declined to answer on content-policy grounds. Not a failure to retry. */
    REFUSAL("refusal"),

    /** The prompt is longer than the model accepts. */
    INPUT_LENGTH("INPUT_LENGTH"),

    /**
     * The formatted provider payload still exceeded the context budget after pruning. Distinct
     * from [INPUT_LENGTH], which is the request the user can shorten: this one means the
     * conversation itself no longer fits.
     */
    FINAL_CONTEXT_OVERFLOW("final_context_overflow"),

    /** A manual compaction the graph declined to attempt; the history is untouched. */
    COMPACTION_SKIPPED("compaction_skipped"),

    /** A manual compaction whose summarizer produced nothing; the history is untouched. */
    COMPACTION_FAILED("compaction_failed"),

    /** The request tripped the deployment's moderation. */
    MODERATION("moderation"),

    /** Sign-in was refused by a rate limiter rather than by bad credentials. */
    AUTH_RATE_LIMITED("auth_rate_limited"),

    /** Sign-in was refused because the account or the IP is banned. */
    AUTH_BANNED("auth_banned"),

    /**
     * The server's `requireSameOrigin` guard rejected an auth request as cross-site.
     *
     * The client's own diagnostic for breaking the header invariant recorded in `DISCOVERY.md`:
     * it is produced by sending `Origin` or `Sec-Fetch-Site`, which this app must never do.
     */
    AUTH_CROSS_ORIGIN("auth_cross_origin"),

    /**
     * Shared-link retrieval was rate-limited. Upstream's `ViolationTypes.SHARE_LIMIT`, which its
     * error registry keys off the same `type` field as the entries above.
     */
    SHARE_LIMIT("share_limit"),

    /**
     * The SSE job 404'd — it completed, expired, or was deleted before this client subscribed.
     * Reconnecting or reopening the conversation is the resolution, not retrying the send.
     */
    STREAM_EXPIRED("stream_expired"),

    /**
     * The model provider itself failed the request — a 5xx, a timeout, a refusal to answer.
     *
     * Ordinary, not exotic: rc2's terminal-run handler raises it for every unattributed provider
     * failure, and composes its message as prose FOLLOWED by the JSON payload — which is why
     * [Companion.parse] extracts the JSON rather than requiring the whole string to be one.
     */
    UPSTREAM_MODEL_ERROR("upstream_model_error"),

    /**
     * Context pruning removed every message, so there was nothing left to send.
     *
     * Reached by an ordinary long conversation on a small window, not by a malformed request.
     */
    EMPTY_MESSAGES("empty_messages"),

    /** The code-interpreter workspace the turn selected could not be reached. */
    CODE_WORKSPACE_UNAVAILABLE("code_workspace_unavailable"),

    /** A stateful code environment was asked for on a deployment that does not permit one. */
    STATEFUL_CODE_ENVIRONMENT_NOT_ALLOWED("stateful_code_environment_not_allowed"),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /**
         * The typed error in a raw stream-error message, or null.
         *
         * Null covers every degrade-to-generic case: the message carries no JSON payload at all,
         * or names a code this client does not recognize. A newer server's code must never crash
         * a client or reach the user as a bare identifier.
         *
         * **The payload is EMBEDDED, not the whole string.** rc2's terminal-run handler composes
         * `"<prose>\n<json>"` — `packages/api/src/agents/failures/terminal.ts` literally returns
         * `` `${UPSTREAM_MODEL_ERROR_FALLBACK}\n${JSON.stringify({ type, status })}` `` — so
         * requiring the message to parse whole is not a malformed-input guard, it rejects a shape
         * the server actively produces. Upstream's client runs `extractJson` (a brace-balanced
         * substring, `client/src/utils/json.ts`) first for the same reason.
         *
         * **The identifier can arrive under `code` or `type`, at the top level or inside an
         * `error` envelope.** `CodeWorkspaceSelectionError` carries `code`, never `type`, and an
         * OpenAI-compatible provider body is shaped `{"error":{"type":…}}`. Upstream reads
         * `readString(json,'code') ?? readString(json,'type')` and unwraps `error` only when the
         * top level names neither — see [identifier], which mirrors that gate.
         *
         * Checked BEFORE the JSON parse, because [MODEL_NOT_FOUND] arrives as provider prose
         * rather than as a typed payload and would otherwise fall straight through to generic.
         */
        fun parse(rawMessage: String): StreamErrorType? {
            if (rawMessage.isBlank()) return null
            if (MODEL_NOT_FOUND_PATTERN.containsMatchIn(rawMessage)) return MODEL_NOT_FOUND
            var from = rawMessage.indexOf('{')
            while (from >= 0) {
                val span = balancedObjectAt(rawMessage, from)
                val payload = span
                    ?.let { runCatching { parser.parseToJsonElement(it) }.getOrNull() }
                    as? JsonObject
                // The FIRST payload that names an identifier settles it, mapped or not. Resolving
                // `byWire` first and searching on for a hit is the difference between this and
                // upstream, and it is the difference between "generic" and "wrong": for
                // `{"type":"invalid_request_error","error":{"type":"moderation"}}` upstream reads
                // the present top-level key, finds no renderer, and shows the provider's own
                // sentence, while a hit-seeking walk descends into the envelope and tells the user
                // their message was blocked by a content filter.
                payload?.identifier()?.let { return byWire[it] }
                // Past a span that PARSED, into one that did not. Re-entering a parsed object
                // re-offers its own children as payloads; skipping a run that is not JSON at all
                // would drop a real payload nested inside prose braces (`Run {id: 1, e:
                // {"type":…}}`), which is the widening this walk exists for.
                from = rawMessage.indexOf('{', from + if (payload != null) span!!.length else 1)
            }
            return null
        }

        /**
         * The identifier this payload names, or null — `code` then `type` at the top level, and
         * the `error` envelope only when the top level names neither, exactly as upstream's
         * `readString(json,'code') ?? readString(json,'type')` and its `topLevelKey == null` gate.
         *
         * Returns the raw string rather than a [StreamErrorType]: presence and recognition are
         * different questions, and collapsing them is what lets an unrecognized identifier fall
         * through to a nested one.
         */
        private fun JsonObject.identifier(): String? =
            ownIdentifier() ?: (this["error"] as? JsonObject)?.ownIdentifier()

        private fun JsonObject.ownIdentifier(): String? {
            // Safe-cast, not `.jsonPrimitive`: that extension throws on an object or array value,
            // and this runs inside the SSE mapping coroutine.
            val code = (this["code"] as? JsonPrimitive)?.contentOrNull
            return code ?: (this["type"] as? JsonPrimitive)?.contentOrNull
        }

        /**
         * The brace-balanced `{…}` run starting at [start], or null if it never closes.
         *
         * **Two deliberate divergences from upstream's `extractJson`**, both widening and neither
         * able to classify anything `byWire` does not already name:
         * - upstream returns only the FIRST balanced run and gives up; [parse] walks every `{`
         *   until one identifies, because prose around the payload can carry braces of its own
         *   (`Template {placeholder} failed. {"type":…}`) and upstream would stop at the first;
         * - quoted braces are skipped here, so a `{` inside a string value cannot end the run
         *   early — upstream's counter has no string state and truncates such a payload.
         */
        private fun balancedObjectAt(raw: String, start: Int): String? {
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until raw.length) {
                val c = raw[i]
                when {
                    escaped -> escaped = false
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
            return null
        }

        /**
         * What a display surface should show for a raw error string: this type's [marker] when
         * the string is recognized, the server's own text when it is not.
         *
         * **The single entry point for classification.** A stream error reaches the user two
         * ways — as the reason a run ended (`StreamEndReason.Error`) and as an in-band `error`
         * content part on the assistant message — and the second one is not a lesser case: an
         * rc1 model-not-found failure persists the message with `error: false` and no text, so
         * the part is the *only* place the failure exists. Classifying at one of the two sites
         * put actionable copy on the snackbar and raw provider JSON in the thread for the same
         * error. Route both through here rather than reaching for [parse] directly, so a second
         * regex home can never be added beside this one.
         *
         * Keeping the server's text on no match is the contract, not a fallback: see [parse].
         */
        fun markerOrText(rawMessage: String): String = parse(rawMessage)?.marker ?: rawMessage

        /**
         * LEGACY fallback for servers at or below v0.8.8-rc1, which have no typed code for
         * [MODEL_NOT_FOUND] and instead surface a LangChain documentation URL embedded in provider
         * prose. rc2 deleted upstream's own copy of this regex (`langChainModelNotFoundUrl`) when
         * `model_not_found` became a typed `ErrorTypes` member, so there is no longer an upstream
         * symbol to mirror and the `scripts/mirrors.json` entry that watched one was retired.
         *
         * Matched anywhere in the text rather than parsed. `\b` is spelled out as a lookahead on a
         * non-word character or end-of-input, because Kotlin's `Regex` on Android is ICU and its
         * `\b` handling around a URL is not worth relying on.
         *
         * Every literal brace stays escaped: a pattern that compiles on the JVM can still throw at
         * class-init under ICU, and no unit test on this side would catch it.
         */
        private val MODEL_NOT_FOUND_PATTERN =
            Regex("""langchain\.com/.*/MODEL_NOT_FOUND(?:/|[^A-Za-z0-9_]|$)""", RegexOption.IGNORE_CASE)

        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Prefix for the marker a ViewModel puts on the shared error channel so the UI can swap
         * in a localized string.
         *
         * The ViewModel layer cannot reach compose resources, and the error channel carries a
         * String — the same constraint `McpViewModel.DEFERRED_MARKER` solves the same way. The
         * alternative, resolving the string in the UI from a second state field, means every
         * error surface has to know about both fields and the two can disagree.
         */
        const val MARKER_PREFIX = "stream_error:"
    }

    /** The marker form of this error, for the shared string-typed error channel. */
    val marker: String get() = "$MARKER_PREFIX$wire"
}
