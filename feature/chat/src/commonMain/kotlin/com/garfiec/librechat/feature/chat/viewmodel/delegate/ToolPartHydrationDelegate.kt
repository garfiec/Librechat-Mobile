package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.feature.chat.util.ToolPartIdentity
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.ToolPartLoad
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import com.garfiec.librechat.feature.chat.util.hydratedChars
import com.garfiec.librechat.feature.chat.util.isFullCopyOf
import com.garfiec.librechat.feature.chat.util.previewedToolParts
import com.garfiec.librechat.feature.chat.util.withHydratedToolParts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Full content of previewed tool calls (v0.8.9-rc1 `?toolPreviews=1`), fetched when a card that
 * shows it opens and held **in memory only**. Never Room: a card can be opened mid-stream, when no
 * Room write is allowed, and a fetched part merged into a row would be indistinguishable from a
 * server answer the next refresh flips back.
 *
 * The UI overlays [loaded] onto the built display path (`withHydratedToolParts`); nothing here
 * touches the tree, `activeBranches` or the streaming anchor. Confined to the scope's Main thread.
 *
 * - [request] is a no-op while loading or loaded, retries from failed, and runs on the ViewModel
 *   scope, so it outlives the card scrolling away. There is no automatic retry.
 * - Everything is dropped on a conversation or account switch, and with the ViewModel.
 * - Bounded by size: past [budgetChars] the least recently opened parts go, never one an open card
 *   holds ([retain] / [release]).
 * - A load that replaces a call this screen held in full with a preview of it seeds the full call
 *   under the new key instead of refetching it (see [isFullCopyOf]).
 */
class ToolPartHydrationDelegate(
    private val scope: CoroutineScope,
    private val messageRepository: MessageRepository,
    /** The backing state's conversation id and messages: the un-hydrated source of truth. */
    private val currentConversationId: () -> String?,
    private val currentMessages: () -> List<Message>,
    private val currentAccountId: () -> String?,
    private val defaultDispatcher: CoroutineDispatcher,
    private val budgetChars: Long = DEFAULT_BUDGET_CHARS,
) {
    private val _loads = MutableStateFlow<Map<ToolPartKey, ToolPartLoad>>(emptyMap())
    private val _loaded = MutableStateFlow<Map<ToolPartKey, AgentToolCall>>(emptyMap())
    private val _statuses = MutableStateFlow<Map<ToolPartKey, ToolPartStatus>>(emptyMap())

    /** Every part known, loading and failed included. */
    val loads: StateFlow<Map<ToolPartKey, ToolPartLoad>> = _loads.asStateFlow()

    /** The fetched calls the display overlays. Changes only when a part lands or leaves. */
    val loaded: StateFlow<Map<ToolPartKey, AgentToolCall>> = _loaded.asStateFlow()

    /** What cards show while their content is not in place. */
    val statuses: StateFlow<Map<ToolPartKey, ToolPartStatus>> = _statuses.asStateFlow()

    /** The one writer: the two views are derived in the same step, so they never disagree. */
    private var parts: Map<ToolPartKey, ToolPartLoad>
        get() = _loads.value
        set(value) {
            _loads.value = value
            val loadedNow = value.mapNotNull { (key, load) -> (load as? ToolPartLoad.Loaded)?.let { key to it.full } }.toMap()
            if (!sameEntries(loadedNow, _loaded.value)) _loaded.value = loadedNow
            val statusesNow = value.mapNotNull { (key, load) ->
                when (load) {
                    ToolPartLoad.Loading -> key to ToolPartStatus.LOADING
                    ToolPartLoad.Failed -> key to ToolPartStatus.FAILED
                    is ToolPartLoad.Loaded -> null
                }
            }.toMap()
            if (!sameEntries(statusesNow, _statuses.value)) _statuses.value = statusesNow
        }

    private val inFlight = mutableMapOf<ToolPartKey, Job>()
    private val lastOpened = mutableMapOf<ToolPartKey, Long>()
    private val pins = mutableMapOf<ToolPartIdentity, Int>()
    private var clock = 0L
    private var scopeId: Pair<String?, String?>? = null

    /**
     * Watches the displayed conversation: a switch (or an account switch) drops everything, and a
     * load that replaces full calls with previews of them seeds those.
     */
    fun start(messages: Flow<Pair<String?, List<Message>>>) {
        scope.launch {
            var previous: List<Message> = emptyList()
            messages.distinctUntilChanged { a, b -> a.first == b.first && a.second === b.second }.collect { (conversationId, current) ->
                if (ensureScope(conversationId)) previous = emptyList()
                val seedScope = scopeId
                val outgoing = previous
                previous = current
                val seeds = withContext(defaultDispatcher) { seedsFor(outgoing, current, parts.keys) }
                if (seeds.isNotEmpty() && scopeId == seedScope) {
                    parts = parts + seeds.mapValues { (_, full) -> ToolPartLoad.Loaded(full, hydratedChars(full)) }
                    seeds.keys.forEach(::touch)
                    evict()
                }
            }
        }
    }

    /** Fetches [key]'s full part unless it is loading or loaded. From [ToolPartLoad.Failed] it retries. */
    fun request(key: ToolPartKey) {
        ensureScope(currentConversationId())
        touch(key)
        when (parts[key]) {
            ToolPartLoad.Loading, is ToolPartLoad.Loaded -> return
            ToolPartLoad.Failed, null -> Unit
        }
        if (inFlight[key]?.isActive == true) return
        val conversationId = currentConversationId() ?: return
        val target = locate(currentMessages(), key) ?: run {
            // The message changed under the card (a reload re-previewed it): its new key asks.
            parts = parts + (key to ToolPartLoad.Failed)
            return
        }
        parts = parts + (key to ToolPartLoad.Loading)
        val requestScope = scopeId
        inFlight[key] = scope.launch {
            val result = fetch(conversationId, target.first, target.second, key)
            inFlight.remove(key)
            // A conversation or account switched while the request was out keeps none of it.
            if (scopeId != requestScope) return@launch
            parts = parts + (key to result)
            if (result is ToolPartLoad.Loaded) evict()
        }
    }

    /** An open card holds its part: [identity] is never evicted while retained. */
    fun retain(identity: ToolPartIdentity) {
        pins[identity] = (pins[identity] ?: 0) + 1
    }

    fun release(identity: ToolPartIdentity) {
        val count = (pins[identity] ?: return) - 1
        if (count <= 0) pins.remove(identity) else pins[identity] = count
    }

    /**
     * [messageId] with every previewed tool call in full, for the clipboard: parts already fetched
     * are reused, the rest fetched at most [COPY_PARALLELISM] at a time. Null when any fetch fails,
     * so nothing shortened is ever copied.
     */
    suspend fun fullMessage(messageId: String): Message? {
        val conversationId = currentConversationId() ?: return null
        val message = currentMessages().firstOrNull { it.messageId == messageId } ?: return null
        val known = loaded.value
        val missing = previewedToolParts(message).mapNotNull { (index, part) ->
            val key = ToolPartKey.of(part.toolCall, part.agentId) ?: return null
            if (key in known) null else Triple(index, key, part)
        }
        val fetched = if (missing.isEmpty()) {
            emptyMap()
        } else {
            val permits = Semaphore(COPY_PARALLELISM)
            val results = coroutineScope {
                missing.map { (index, key, _) ->
                    async { key to permits.withPermit { fetch(conversationId, messageId, index, key) } }
                }.awaitAll()
            }
            if (results.any { it.second !is ToolPartLoad.Loaded }) return null
            val landed = results.associate { (key, load) -> key to (load as ToolPartLoad.Loaded) }
            if (currentConversationId() == conversationId) {
                parts = parts + landed
                landed.keys.forEach(::touch)
                evict()
            }
            landed.mapValues { it.value.full }
        }
        val hydrated = message.withHydratedToolParts(known + fetched)
        return hydrated.takeIf { full -> full.content.orEmpty().none { it.toolCall?.isPreview == true } }
    }

    private suspend fun fetch(conversationId: String, messageId: String, partIndex: Int, key: ToolPartKey): ToolPartLoad {
        val result = messageRepository.getToolCallPart(
            conversationId = conversationId,
            messageId = messageId,
            partIndex = partIndex,
            toolCallId = key.identity.toolCallId,
            stepId = key.identity.stepId,
            agentId = key.identity.agentId,
        )
        return when (result) {
            is Result.Success -> ToolPartLoad.Loaded(result.data, hydratedChars(result.data))
            is Result.Error -> {
                // 404: the message changed or is gone, or the identity repeated. 400 should not
                // happen (only calls with an id are previewed). Either way the user retries.
                Logger.w(result.exception) { "Tool call part failed to load (${key.identity.toolCallId})" }
                ToolPartLoad.Failed
            }
            is Result.Loading -> ToolPartLoad.Failed
        }
    }

    /** Drops everything when the conversation or account differs from the last seen. True when it did. */
    private fun ensureScope(conversationId: String?): Boolean {
        val next = conversationId to currentAccountId()
        if (scopeId == next) return false
        val had = scopeId != null
        scopeId = next
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        lastOpened.clear()
        if (parts.isNotEmpty()) parts = emptyMap()
        return had
    }

    private fun touch(key: ToolPartKey) {
        lastOpened[key] = ++clock
    }

    /** Drops least recently opened loaded parts past the budget, never one an open card holds. */
    private fun evict() {
        val current = parts
        var total = current.values.sumOf { (it as? ToolPartLoad.Loaded)?.chars ?: 0L }
        if (total <= budgetChars) return
        val candidates = current.entries
            .filter { it.value is ToolPartLoad.Loaded && it.key.identity !in pins }
            .sortedBy { lastOpened[it.key] ?: 0L }
        val dropped = mutableSetOf<ToolPartKey>()
        for ((key, load) in candidates) {
            if (total <= budgetChars) break
            total -= (load as ToolPartLoad.Loaded).chars
            dropped += key
        }
        if (dropped.isNotEmpty()) {
            parts = current - dropped
            dropped.forEach(lastOpened::remove)
        }
    }

    companion object {
        /** ~4M characters: one subagent transcript can be most of the payload previews exist to avoid. */
        const val DEFAULT_BUDGET_CHARS: Long = 4_000_000L

        /** At most this many part fetches at once for a whole-message copy. */
        const val COPY_PARALLELISM: Int = 8
    }
}

/** The message holding [key]'s call, and the call's raw index in its content. */
internal fun locate(messages: List<Message>, key: ToolPartKey): Pair<String, Int>? {
    for (message in messages) {
        message.content?.forEachIndexed { index, part ->
            if (part.type == ContentType.TOOL_CALL && ToolPartIdentity.of(part.toolCall, part.agentId) == key.identity &&
                ToolPartKey.of(part.toolCall, part.agentId) == key
            ) {
                return message.messageId to index
            }
        }
    }
    return null
}

/**
 * Full calls in [previous] that [current] now carries as previews of themselves (same identity,
 * matching lengths), keyed by the preview's key. Parts already known are skipped.
 */
internal fun seedsFor(
    previous: List<Message>,
    current: List<Message>,
    known: Set<ToolPartKey>,
): Map<ToolPartKey, AgentToolCall> {
    if (previous.isEmpty()) return emptyMap()
    val wanted = current.flatMap { message ->
        message.content.orEmpty().mapNotNull { part ->
            val call = part.toolCall?.takeIf { part.type == ContentType.TOOL_CALL } ?: return@mapNotNull null
            val key = ToolPartKey.of(call, part.agentId) ?: return@mapNotNull null
            if (key in known) null else key to call
        }
    }
    if (wanted.isEmpty()) return emptyMap()
    val fullByIdentity = HashMap<ToolPartIdentity, AgentToolCall>()
    previous.forEach { message ->
        message.content.orEmpty().forEach { part ->
            val call = part.toolCall
            if (part.type == ContentType.TOOL_CALL && call != null && !call.isPreview) {
                ToolPartIdentity.of(call, part.agentId)?.let { fullByIdentity[it] = call }
            }
        }
    }
    return wanted.mapNotNull { (key, preview) ->
        fullByIdentity[key.identity]?.takeIf { isFullCopyOf(it, preview) }?.let { key to it }
    }.toMap()
}

/** Same keys mapping to the same instances: no deep compare of what can be megabytes of output. */
private fun <K, V> sameEntries(a: Map<K, V>, b: Map<K, V>): Boolean =
    a.size == b.size && a.all { (key, value) -> b[key] === value }
