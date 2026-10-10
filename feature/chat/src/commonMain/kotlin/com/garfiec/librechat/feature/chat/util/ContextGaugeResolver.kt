package com.garfiec.librechat.feature.chat.util

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.EstimateDetail
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.feature.chat.components.toolCallJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToInt

/*
 * The context gauge's two client-side sources on a v0.8.8+ server, which no longer offers the
 * context-projection endpoint: the snapshot the server saves on each response, and an estimate
 * for a branch that has none. Pure functions over the displayed branch (root → tail), so the
 * caller can recompute them on every emission — a cache read and the network refresh that
 * follows it often share a tail id and differ only in `metadata`.
 *
 * Ported from upstream web at v0.8.8: `hydrateSnapshots` (client/src/store/usage.ts) and
 * `findBranchSnapshotAnchor`, `toEntry`, `sumBranch`, `prunedBranchTokens`
 * (client/src/utils/tokens.ts), plus the estimate block of `useTokenUsage`.
 */

private val log = Logger.withTag("ContextGauge")

/**
 * The context snapshot the server saved on this response (`metadata.contextUsage`), or null.
 * A malformed blob is server data, not a programming error, so it reads as absent.
 */
fun Message.persistedContextUsage(): ContextUsage? {
    val blob = metadata?.get("contextUsage") as? JsonObject ?: return null
    return try {
        toolCallJson.decodeFromJsonElement(ContextUsage.serializer(), blob)
    } catch (e: SerializationException) {
        log.d(e) { "Skipping malformed metadata.contextUsage on $messageId" }
        null
    } catch (e: IllegalArgumentException) {
        log.d(e) { "Skipping malformed metadata.contextUsage on $messageId" }
        null
    }
}

/** Pre-invoke size of the compacted context when this response's turn summarized history. */
private fun Message.summaryUsedTokens(): Int =
    normalizeTokenCount((metadata?.get("summaryUsedTokens") as? JsonPrimitive)?.doubleOrNull)

/** The snapshot saved on the message with [messageId], if that message is on [branch]. */
fun persistedSnapshotAt(branch: List<Message>, messageId: String): ContextUsage? =
    branch.lastOrNull { it.messageId == messageId }?.persistedContextUsage()

/**
 * The deepest saved snapshot on [branch]: the first one walking from the tail toward the root.
 *
 * Stops with none at a response that summarized the history without saving a snapshot of its
 * own. Crossing it would revive an older snapshot of context that the summary discarded; the
 * estimate's summary baseline covers that branch instead.
 */
fun deepestPersistedSnapshot(branch: List<Message>): ContextUsage? {
    for (message in branch.asReversed()) {
        message.persistedContextUsage()?.let { return it }
        if (message.summaryUsedTokens() > 0) return null
    }
    return null
}

/**
 * Client-side estimate for a branch with no saved snapshot (history from before the server
 * saved them, imports, a branch never generated with the feature on).
 *
 * Each message counts its stored `tokenCount`, or chars/4 when it has none. A quoted user turn
 * always estimates from its text plus quotes, because its stored count is unreliable. The walk
 * stops after the deepest response that summarized history, whose pre-invoke size becomes the
 * baseline. The cached instruction overhead is added unless a baseline already includes it.
 * When the sum overflows the window, it keeps the newest messages that fit, as the send path
 * prunes before calling the model.
 *
 * Returns null without a window: the gauge is a ratio, so it stays hidden rather than showing a
 * bare count (upstream shows used tokens with 0%).
 */
fun estimateDetail(branch: List<Message>, windowTokens: Int?, overheadTokens: Int): EstimateDetail? {
    if (windowTokens == null || windowTokens <= 0 || branch.isEmpty()) return null
    val entries = branch.asReversed().map { it.toEntry() }

    var input = 0
    var output = 0
    var estimated = 0
    var toolTokens = 0
    var summaryBaseline = 0
    for (entry in entries) {
        when {
            entry.tokenCount > 0 && entry.isCreatedByUser -> input += entry.tokenCount
            entry.tokenCount > 0 -> output += entry.tokenCount
            else -> estimated += entry.estTokens
        }
        toolTokens += minOf(entry.estToolTokens, entry.contribution)
        if (entry.summaryUsedTokens > 0) {
            summaryBaseline = entry.summaryUsedTokens
            break
        }
    }
    val overhead = if (summaryBaseline > 0) 0 else overheadTokens.coerceAtLeast(0)
    val rawMessageTokens = input + output + estimated
    var messageTokens = rawMessageTokens
    var toolCallTokens = minOf(toolTokens, messageTokens)

    val messageBudget = (windowTokens - summaryBaseline - overhead).coerceAtLeast(0)
    if (rawMessageTokens > messageBudget) {
        val pruned = prunedBranchTokens(entries, messageBudget)
        messageTokens = pruned.first
        toolCallTokens = minOf(pruned.second, messageTokens)
    }
    return EstimateDetail(
        input = input,
        output = output,
        estimated = estimated,
        toolCallTokens = toolCallTokens,
        overheadTokens = overhead,
        summaryBaseline = summaryBaseline,
        messageTokens = messageTokens,
        messagesPruned = messageTokens < rawMessageTokens,
    )
}

/**
 * [estimateDetail] as a gauge reading. The overhead is reported as the instruction share; the
 * breakdown renders estimate rows from [estimateDetail] itself, never from this breakdown.
 */
fun estimateContextUsage(branch: List<Message>, windowTokens: Int?, overheadTokens: Int): ContextUsage? {
    val detail = estimateDetail(branch, windowTokens, overheadTokens) ?: return null
    return ContextUsage(
        breakdown = TokenBudgetBreakdown(
            maxContextTokens = windowTokens ?: 0,
            instructionTokens = detail.overheadTokens,
            systemMessageTokens = detail.overheadTokens,
            summaryTokens = detail.summaryBaseline,
            messageTokens = detail.messageTokens,
        ),
    )
}

/**
 * Tokens of the most recent exchange, the reply plus the user turn it answers: what a
 * summarization would keep. While streaming, the branch tail is the user message (the reply
 * renders outside the tree), so only it counts. [summaryOutputTokens] removes a compacting
 * turn's summary from its reply, which the snapshot holds outside `messageTokens`.
 * Upstream `latestExchangeTokens`, adapted to the mobile tail.
 */
fun latestExchangeTokens(branch: List<Message>, isStreaming: Boolean, summaryOutputTokens: Int): Int {
    val tail = branch.lastOrNull()?.toEntry() ?: return 0
    if (isStreaming) return tail.exchangeContribution
    var reply = tail.exchangeContribution
    if (reply > 0 && tail.summaryUsedTokens > 0) reply = (reply - summaryOutputTokens.coerceAtLeast(0)).coerceAtLeast(0)
    val parent = branch.getOrNull(branch.lastIndex - 1)?.toEntry()?.exchangeContribution ?: 0
    return reply + parent
}

/** Newest-first walk keeping what fits in [budget]; stops at the first message that doesn't. Returns (tokens, tool share). */
private fun prunedBranchTokens(entriesNewestFirst: List<TokenEntry>, budget: Int): Pair<Int, Int> {
    if (budget <= 0) return 0 to 0
    var total = 0
    var toolTotal = 0
    for (entry in entriesNewestFirst) {
        if (total + entry.contribution > budget) break
        total += entry.contribution
        toolTotal += minOf(entry.estToolTokens, entry.contribution)
        // Older turns are inside the summary baseline the caller already reserved.
        if (entry.summaryUsedTokens > 0) break
    }
    return total to toolTotal
}

/** Upstream `TokenEntry`: a message's stored count, its char estimate, and its tool-call shares. */
private class TokenEntry(
    val tokenCount: Int,
    val estTokens: Int,
    val estToolTokens: Int,
    val estToolResultTokens: Int,
    val isCreatedByUser: Boolean,
    val summaryUsedTokens: Int,
) {
    val contribution: Int get() = if (tokenCount > 0) tokenCount else estTokens

    /** Completion counts include call arguments but not tool results, so those are added back. */
    val exchangeContribution: Int get() = if (tokenCount > 0) tokenCount + estToolResultTokens else estTokens
}

private fun Message.toEntry(): TokenEntry {
    val stored = normalizeTokenCount(tokenCount?.toDouble())
    // A quoted user turn's stored count is unreliable (a text-only edit recounts it without the
    // quotes), so it always estimates from the merged text, as the server does.
    val quoted = isCreatedByUser && !quotes.isNullOrEmpty()
    val estTokens = when {
        quoted -> roundQuarter(messageChars() + quotes.orEmpty().sumOf { it.length })
        stored == 0 -> roundQuarter(messageChars())
        else -> 0
    }
    var toolChars = 0
    var resultChars = 0
    content?.forEach { part ->
        val call = part.toolCall?.takeIf { part.type == ContentType.TOOL_CALL } ?: return@forEach
        val output = call.output.orEmpty().length
        toolChars += call.name.orEmpty().length + call.argsChars() + output
        resultChars += output
    }
    return TokenEntry(
        tokenCount = if (quoted) 0 else stored,
        estTokens = estTokens,
        estToolTokens = roundQuarter(toolChars),
        estToolResultTokens = roundQuarter(resultChars),
        isCreatedByUser = isCreatedByUser,
        summaryUsedTokens = summaryUsedTokens(),
    )
}

/** Chars the send path formats: structured content when present (tool calls included), else text. */
private fun Message.messageChars(): Int {
    val parts = content
    if (!parts.isNullOrEmpty()) return parts.sumOf { it.textChars() }
    return text.length
}

/** Reasoning and error parts are stripped before the send path counts, so they add nothing. */
private fun MessageContentPart.textChars(): Int = when (type) {
    ContentType.THINK, ContentType.ERROR -> 0
    ContentType.TOOL_CALL -> toolCall?.let { call ->
        call.name.orEmpty().length + call.argsChars() + call.output.orEmpty().length
    } ?: 0
    else -> text?.length ?: 0
}

private fun AgentToolCall.argsChars(): Int {
    val args = args ?: return 0
    return if (args is JsonPrimitive && args.isString) args.content.length else args.toString().length
}

private fun roundQuarter(chars: Int): Int = (chars / 4.0).roundToInt()

/** Upstream `normalizeTokenCount`: a positive finite count, floored; anything else is 0. */
private fun normalizeTokenCount(value: Double?): Int =
    if (value != null && value.isFinite() && value > 0) value.toInt() else 0
