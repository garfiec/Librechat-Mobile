package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.usage.ResponseUsage
import com.garfiec.librechat.core.model.usage.TokenUsage
import com.garfiec.librechat.core.model.usage.UsageAmount
import com.garfiec.librechat.feature.chat.components.toolCallJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject

/*
 * Provider usage behind the context breakdown's Totals and Cost (v0.8.8). Settled figures come
 * from each reply's persisted `metadata.usage`; the reply still streaming is covered by the
 * pending usage folded from its live `on_token_usage` events.
 *
 * Ported from upstream web at v0.8.8: `readPersistedUsage`, `mergeUsage`, `normalizeUsageUnits`
 * (client/src/utils/tokens.ts), `foldUsage` (client/src/hooks/SSE/useUsageHandler.ts) and
 * `cacheSubsetProviders` (packages/data-provider/src/schemas.ts).
 */

private val log = Logger.withTag("UsageTotals")

/**
 * Usage of the run in progress, folded from its live events. Counted until the reply it
 * belongs to carries its own persisted usage; [anchorResponseId] names that reply once the
 * final frame has arrived.
 */
@Immutable
data class PendingUsage(
    val usage: UsageAmount = UsageAmount.EMPTY,
    /** The subagent share of [usage]: shown separately, and committed to the session at the end. */
    val subagent: UsageAmount = UsageAmount.EMPTY,
    /** One key per model call already counted, so an event replayed on resume is not counted twice. */
    val foldedKeys: Set<String> = emptySet(),
    val anchorResponseId: String? = null,
) {
    val isEmpty: Boolean get() = foldedKeys.isEmpty()

    companion object {
        val EMPTY = PendingUsage()
    }
}

/** Providers whose `input_tokens` already include the cached share (upstream `cacheSubsetProviders`). */
private val CACHE_SUBSET_PROVIDERS = setOf(
    "openAI", "azureOpenAI", "google", "vertexai", "xai", "deepseek", "openrouter", "moonshot", "anthropic",
)

/**
 * One call's usage in the units billing uses: input is the uncached portion, output includes
 * the completion a provider under-reports in `output_tokens` but counts in `total_tokens`.
 */
fun TokenUsage.normalizedUnits(): UsageAmount {
    val rawInput = (inputTokens ?: 0).coerceAtLeast(0)
    val rawOutput = (outputTokens ?: 0).coerceAtLeast(0)
    val total = (totalTokens ?: 0).coerceAtLeast(0)
    val cacheWrite = (inputTokenDetails?.cacheCreation ?: 0).coerceAtLeast(0)
    val cacheRead = (inputTokenDetails?.cacheRead ?: 0).coerceAtLeast(0)
    val includesCache = provider?.let { it in CACHE_SUBSET_PROVIDERS } ?: (cacheWrite + cacheRead <= rawInput)
    val cacheAdjustment = if (includesCache) 0 else cacheRead + cacheWrite
    val output = if (total > rawInput + rawOutput + cacheAdjustment) total - rawInput - cacheAdjustment else rawOutput
    val knownCost = cost?.takeIf { it.isFinite() && it >= 0 }
    return UsageAmount(
        input = if (includesCache) (rawInput - cacheRead - cacheWrite).coerceAtLeast(0) else rawInput,
        output = output,
        cacheWrite = cacheWrite,
        cacheRead = cacheRead,
        cost = knownCost ?: 0.0,
        costKnown = knownCost != null,
    )
}

/**
 * Adds one live (or backfilled) usage event to [this], once. Every bucket counts — summary
 * passes, subagent runs, activity labels — because each is a billed model call, as on web.
 */
fun PendingUsage.fold(event: TokenUsage): PendingUsage {
    val key = if (event.runId != null && event.seq != null) "${event.runId}:${event.seq}" else event.toString()
    if (key in foldedKeys) return this
    val units = event.normalizedUnits()
    return copy(
        usage = usage + units,
        subagent = if (event.usageType == SUBAGENT_BUCKET) subagent + units else subagent,
        foldedKeys = foldedKeys + key,
    )
}

private const val SUBAGENT_BUCKET = "subagent"

/** The usage the server saved on this reply (`metadata.usage`), or null. */
fun Message.persistedUsage(): UsageAmount? {
    val blob = metadata?.get("usage") as? JsonObject ?: return null
    val saved = try {
        toolCallJson.decodeFromJsonElement(ResponseUsage.serializer(), blob)
    } catch (e: SerializationException) {
        log.d(e) { "Skipping malformed metadata.usage on $messageId" }
        return null
    } catch (e: IllegalArgumentException) {
        log.d(e) { "Skipping malformed metadata.usage on $messageId" }
        return null
    }
    val knownCost = saved.cost?.takeIf { it.isFinite() && it >= 0 }
    return UsageAmount(
        input = saved.input.coerceAtLeast(0),
        output = saved.output.coerceAtLeast(0),
        cacheWrite = saved.cacheWrite.coerceAtLeast(0),
        cacheRead = saved.cacheRead.coerceAtLeast(0),
        cost = knownCost ?: 0.0,
        // Saved with `contextCost` off: absent, and must not read as a real $0.00.
        costKnown = knownCost != null,
    )
}

/**
 * Persisted usage summed over [scope], plus [pending] while it is not yet represented there.
 *
 * Pending counts while the run is live ([isStreaming]); once anchored to its reply, only while
 * that reply lacks its own `metadata.usage` — the final frame usually brings it, so the
 * emission that adds the reply drops pending in the same frame. A reply not yet in [allMessages]
 * at all (the comparison reload gap) still counts; one present but outside [scope] does not.
 */
fun usageOver(
    scope: List<Message>,
    allMessages: List<Message>,
    pending: PendingUsage,
    isStreaming: Boolean,
): UsageAmount {
    var sum = UsageAmount.EMPTY
    for (message in scope) message.persistedUsage()?.let { sum += it }
    return if (pendingCounts(scope, allMessages, pending, isStreaming)) sum + pending.usage else sum
}

private fun pendingCounts(
    scope: List<Message>,
    allMessages: List<Message>,
    pending: PendingUsage,
    isStreaming: Boolean,
): Boolean {
    if (pending.isEmpty) return false
    if (isStreaming) return true
    val anchor = pending.anchorResponseId ?: return true
    scope.lastOrNull { it.messageId == anchor }?.let { return it.persistedUsage() == null }
    return allMessages.none { it.messageId == anchor }
}
