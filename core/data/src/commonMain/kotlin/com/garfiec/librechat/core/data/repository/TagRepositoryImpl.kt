package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.identity.flatMapAccountOrEmpty
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.data.mapper.toModels
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Talks to the conversation endpoints directly (not through [ConversationRepository]) because
 * [ConversationRepository] calls back into this repository after a delete, to refresh tag counts.
 *
 * Writes run in [applicationScope] (see [detached]): a screen that goes away mid-write cancels its
 * wait, never the write's later requests or the rollback and refresh that follow them.
 */
class TagRepositoryImpl(
    private val tagsApi: TagsApi,
    private val tagDao: ConversationTagDao,
    private val conversationsApi: ConversationsApi,
    private val conversationDao: ConversationDao,
    private val activeAccountProvider: ActiveAccountProvider,
    private val changes: TagChangeSignal,
    private val applicationScope: CoroutineScope,
) : TagRepository {

    /**
     * Serializes the read-then-write of a chat's tags: two quick toggles would otherwise both read
     * the same set and the second PUT would drop the first change.
     */
    private val chatTagWrites = Mutex()

    /** One [reorderAfter] at a time: each works out its slot from the order the one before it left. */
    private val moves = Mutex()

    override val revision: StateFlow<Long> = changes.revision

    override val events: SharedFlow<TagEvent> = changes.events

    override fun observeTags(): Flow<List<ConversationTag>> =
        activeAccountProvider.flatMapAccountOrEmpty(emptyList()) { account ->
            tagDao.observeTagsForAccount(account.value).map { rows ->
                rows.toModels().filterNot { it.tag.isNullOrBlank() }
            }
        }

    override fun observeConversationTags(conversationId: String): Flow<List<String>> =
        activeAccountProvider.flatMapAccountOrEmpty(emptyList()) { account ->
            conversationDao.observeByIdForAccount(conversationId, account.value).map { row ->
                row?.toModel()?.tags.orEmpty().cleaned()
            }
        }.distinctUntilChanged()

    override fun observeCachedTags(conversationIds: List<String>): Flow<Map<String, List<String>>> {
        val ids = conversationIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())
        return activeAccountProvider.flatMapAccountOrEmpty(emptyMap()) { account ->
            // Chunked under SQLite's bind limit; a loaded list rarely needs more than one.
            val chunks = ids.chunked(IDS_PER_QUERY).map { chunk -> conversationDao.observeTagsFor(account.value, chunk) }
            combine(chunks) { rows ->
                rows.flatMap { it }.associate { row -> row.conversationId to decodeTags(row.tags).cleaned() }
            }
        }.distinctUntilChanged()
    }

    override suspend fun refreshTags(): Result<Unit> = safeApiCall {
        val account = activeAccountProvider.currentAccountId() ?: return@safeApiCall
        val tags = tagsApi.getTags().filterNot { it.tag.isNullOrBlank() }
        // Scoped replace: only this account's tag rows are swapped, never another account's.
        tagDao.replaceAllForAccount(
            account.value,
            tags.map { it.toEntity().copy(accountId = account.value) },
        )
    }

    override suspend fun getConversationTags(conversationId: String): Result<List<String>> {
        val accountId = activeAccountProvider.currentAccountId()?.value
        return safeApiCall {
            // After any write still out: its GET may already be answered and its PUT not, and a
            // checklist reopened in between would show the tick it is about to save as off.
            chatTagWrites.withLock { fetchChatTags(conversationId, accountId) }
        }
    }

    override suspend fun setConversationTag(
        conversationId: String,
        tag: String,
        attached: Boolean,
    ): Result<List<String>> {
        // Not trimmed: the name comes from the server's list, and web stores names as typed, so
        // " Work" is a different bookmark from "Work".
        if (tag.isBlank()) return Result.Error(IllegalArgumentException("Blank tag name"), "Blank tag name")
        // The account of the tap, not of whenever the queue gets to it: the chat id belongs to it.
        val accountId = activeAccountProvider.currentAccountId()?.value
        return detached {
            safeApiCall {
                val stored = chatTagWrites.withLock { writeChatTag(conversationId, tag, attached, accountId) }
                afterChange()
                stored
            }
        }
    }

    override suspend fun createTag(
        name: String,
        description: String?,
        addToConversationId: String?,
    ): Result<CreatedTag> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error(IllegalArgumentException("Blank tag name"), "Blank tag name")
        val accountId = activeAccountProvider.currentAccountId()?.value
        return detached { createDetached(trimmed, description, addToConversationId, accountId) }
    }

    private suspend fun createDetached(
        trimmed: String,
        description: String?,
        addToConversationId: String?,
        accountId: String?,
    ): Result<CreatedTag> {
        val posted = safeApiCall {
            requireLiveAccount(accountId)
            tagsApi.createTag(
                tag = trimmed,
                description = description?.takeIf { it.isNotBlank() },
                addToConversation = addToConversationId != null,
                conversationId = addToConversationId,
            )
        }
        val created = when (posted) {
            is Result.Success -> posted.data
            is Result.Error -> return posted
            Result.Loading -> return Result.Loading
        }
        val attach = if (addToConversationId == null) {
            null
        } else {
            // The server attaches only a NEW name: an existing one comes back early, unattached.
            // The read-then-write covers both, and caches the chat's tags either way.
            safeApiCall {
                chatTagWrites.withLock {
                    writeChatTag(
                        conversationId = addToConversationId,
                        name = created.tag?.takeIf { it.isNotBlank() } ?: trimmed,
                        attached = true,
                        accountId = accountId,
                        knownToExist = true,
                    )
                }
            }
        }
        // The bookmark exists now even if the attach failed: every list must show it.
        afterChange()
        return Result.Success(CreatedTag(created, attachFailed = attach is Result.Error))
    }

    override suspend fun updateTag(
        name: String,
        newName: String?,
        description: String?,
        position: Int?,
    ): Result<ConversationTag> {
        val renamed = newName?.trim()
        if (renamed != null && renamed.isEmpty()) {
            return Result.Error(IllegalArgumentException("Blank tag name"), "Blank tag name")
        }
        val accountId = activeAccountProvider.currentAccountId()?.value
        return detached { updateDetached(name, renamed, description, position, accountId) }
    }

    private suspend fun updateDetached(
        name: String,
        renamed: String?,
        description: String?,
        position: Int?,
        accountId: String?,
    ): Result<ConversationTag> {
        val result = safeApiCall {
            val updated = tagsApi.updateTag(
                currentTag = name,
                newTag = renamed?.takeIf { it != name },
                description = description,
                position = position,
            )
            if (renamed != null && renamed != name) {
                if (accountId != null) {
                    // The server renamed it on every chat; follow in the cached rows.
                    rewriteCachedChatTags(accountId) { tags -> tags.map { if (it == name) renamed else it }.distinct() }
                }
                changes.emit(TagEvent.Renamed(from = name, to = renamed))
            }
            afterChange()
            updated
        }
        // Still an error, but the list here may be stale, or every retry would fail the same way.
        when {
            // Deleted or renamed elsewhere: re-read so the stale card goes.
            result.isNotFound() -> afterChange()
            // Most likely a name made elsewhere (the server answers "Tag already exists" with a 500):
            // re-read, so the editor's duplicate check sees it. Nothing changed here, so no bump.
            result is Result.Error && renamed != null && renamed != name -> {
                if (refreshTags() is Result.Error) Logger.w { "Tag list refresh after a refused rename failed" }
            }
        }
        return result
    }

    override suspend fun deleteTag(name: String): Result<Unit> {
        val accountId = activeAccountProvider.currentAccountId()?.value
        return detached {
            safeApiCall {
                deleteOnServer(name)
                if (accountId != null) {
                    rewriteCachedChatTags(accountId) { tags -> tags - name }
                }
                changes.emit(TagEvent.Deleted(name))
                afterChange()
            }
        }
    }

    override suspend fun reorderTag(name: String, toIndex: Int): Result<Unit> {
        val accountId = activeAccountProvider.currentAccountId()?.value
            ?: return Result.Error(IllegalStateException("No resolved account"), "No resolved account")
        return detached { reorderDetached(name, toIndex, accountId) }
    }

    override suspend fun reorderAfter(name: String, after: String?): Result<Unit> {
        val accountId = activeAccountProvider.currentAccountId()?.value
            ?: return Result.Error(IllegalStateException("No resolved account"), "No resolved account")
        return detached {
            moves.withLock {
                // The re-read fetches whichever account is live: past a switch it isn't this one's.
                if (activeAccountProvider.currentAccountId()?.value != accountId) {
                    return@withLock Result.Error(AccountSwitchedException(), "Account switched")
                }
                val fresh = refreshTags()
                if (fresh is Result.Error) return@withLock fresh
                val order = tagDao.getTagsForAccount(accountId)
                    .filter { row -> row.tag.isNotBlank() && row.tag != SAVED_TAG }
                    .map { it.tag }
                val target = placeAfter(order, name, after)
                if (target == null || target == order.indexOf(name)) {
                    Result.Success(Unit)
                } else {
                    reorderDetached(name, target, accountId)
                }
            }
        }
    }

    private suspend fun reorderDetached(name: String, toIndex: Int, accountId: String): Result<Unit> {
        val snapshot = tagDao.getTagsForAccount(accountId)
        val shown = snapshot.filter { row -> row.tag.isNotBlank() && row.tag != SAVED_TAG }
        val from = shown.indexOfFirst { it.tag == name }
        if (from < 0 || toIndex !in shown.indices) {
            return Result.Error(IllegalArgumentException("No bookmark $name at $toIndex"), "Bookmark not found")
        }
        if (from == toIndex) return Result.Success(Unit)
        // Tied positions first get distinct ones: a move onto a tie changes nothing on the server.
        val spread = if (shown.zipWithNext().all { (a, b) -> a.position < b.position }) null else spreadPositions(snapshot, shown)
        val rows = spread?.rows ?: snapshot
        val visible = shown.map { row -> rows.first { it.id == row.id } }
        // The server moves the tag to a stored `position` and shifts the ones in between, and stored
        // positions needn't be 0..n-1 (and the list here hides Saved), so send the position of the
        // tag now in the target slot rather than the index.
        val moved = visible[from]
        val target = visible[toIndex].position
        tagDao.upsertAll(shiftPositions(rows, moved, target))
        val result = safeApiCall {
            spread?.moves?.forEach { (tag, position) ->
                requireLiveAccount(accountId)
                tagsApi.updateTag(currentTag = tag, position = position)
            }
            requireLiveAccount(accountId)
            tagsApi.updateTag(currentTag = name, position = target)
        }
        return when (result) {
            is Result.Success -> {
                refreshTags()
                changes.bump()
                Result.Success(Unit)
            }
            is Result.Error -> {
                tagDao.replaceAllForAccount(accountId, snapshot)
                when {
                    // Another account is live now: a re-read would fetch its list, not this one's.
                    result.exception is AccountSwitchedException -> Unit
                    // Gone from the server (deleted or renamed elsewhere): the list must stop showing it.
                    result.isNotFound() -> afterChange()
                    // Part of the spread may have landed: take the server's order over the old one.
                    spread != null -> refreshTags()
                }
                result
            }
            Result.Loading -> Result.Loading
        }
    }

    /**
     * Runs [block] in [applicationScope] and waits for it. The caller's cancellation (a screen
     * cleared mid-write) ends only the wait: requests not yet sent still go, and a write that took
     * several requests still finishes, or is rolled back and re-read, instead of stopping halfway.
     * Each leg still checks the account with [requireLiveAccount]. Started undispatched, so writes
     * queue on [chatTagWrites] in the order they were asked.
     */
    private suspend fun <T> detached(block: suspend () -> T): T =
        applicationScope.async(start = CoroutineStart.UNDISPATCHED) { block() }.await()

    override suspend fun clearCache() {
        // Scope to the active account when known; logout's scoped DELETE is the authoritative purge.
        activeAccountProvider.currentAccountId()?.let { tagDao.deleteAllForAccount(it.value) }
    }

    /**
     * The chat's tags as the server has them (the list endpoint omits them, so the cache can't be
     * trusted). Caches the whole server row, tags included.
     */
    private suspend fun fetchChatTags(conversationId: String, accountId: String?): List<String> {
        requireLiveAccount(accountId)
        val conversation = conversationsApi.getConversation(conversationId)
        val tags = conversation.tags.cleaned()
        if (accountId != null) {
            conversationDao.upsert(conversation.copy(tags = tags).toEntity().copy(accountId = accountId))
        }
        return tags
    }

    /**
     * Must run under [chatTagWrites]. Adds or removes [name] against the server's set, so a write
     * the server already holds (a stale label, a second tap) sends nothing. [knownToExist] skips
     * the create for a name just created.
     *
     * An attach always posts the name first, whatever the cache holds: the cached tag list can be
     * stale (deleted or renamed on the web), and the chat PUT would bring a missing name back at
     * position 0. The server returns an existing name unchanged, and files a missing one at the end.
     */
    private suspend fun writeChatTag(
        conversationId: String,
        name: String,
        attached: Boolean,
        accountId: String?,
        knownToExist: Boolean = false,
    ): List<String> {
        val current = fetchChatTags(conversationId, accountId)
        val wanted = when {
            attached && name !in current -> current + name
            !attached -> current - name
            else -> current
        }
        if (wanted == current) return current
        if (attached && !knownToExist) {
            requireLiveAccount(accountId)
            tagsApi.createTag(tag = name)
        }
        requireLiveAccount(accountId)
        val stored = tagsApi.updateConversationTags(conversationId, wanted).cleaned()
        if (accountId != null) {
            conversationDao.updateTags(
                conversationId,
                encodeTags(stored),
                Clock.System.now().toEpochMilliseconds(),
                accountId,
            )
        }
        return stored
    }

    /** Deletes [name] on the server; one already gone (deleted or renamed elsewhere) is what was asked. */
    private suspend fun deleteOnServer(name: String) {
        try {
            tagsApi.deleteTag(name)
        } catch (e: ApiException) {
            if (e.statusCode != HTTP_NOT_FOUND) throw e
            Logger.i { "Bookmark to delete was already gone on the server" }
        }
    }

    private fun Result<*>.isNotFound(): Boolean =
        ((this as? Result.Error)?.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND

    /**
     * Throws [AccountSwitchedException] once the live account is no longer [accountId], the one the
     * write was asked under. The transport sends whatever account is live, so a leg sent after a
     * switch would carry the old account's chat id to the new account's server (a cross-server id
     * disclosure and a sure 404), or make a bookmark there, as `originTransportAllowed` guards
     * against for a streamed finalize.
     */
    private fun requireLiveAccount(accountId: String?) {
        if (activeAccountProvider.currentAccountId()?.value != accountId) throw AccountSwitchedException()
    }

    private suspend fun rewriteCachedChatTags(accountId: String, transform: (List<String>) -> List<String>) {
        for (row in conversationDao.taggedForAccount(accountId)) {
            val tags = row.toModel().tags
            val rewritten = transform(tags)
            if (rewritten != tags) conversationDao.replaceTags(row.conversationId, encodeTags(rewritten), accountId)
        }
    }

    /** Counts and order changed with the write: re-read them, then tell every bookmark surface. */
    private suspend fun afterChange() {
        val refreshed = refreshTags()
        if (refreshed is Result.Error) Logger.w { "Tag write succeeded but the tag list refresh failed" }
        changes.bump()
    }

    /** Drops blank entries only: a name is opaque, and trimming one would send back a different tag. */
    private fun List<String>.cleaned(): List<String> = filter { it.isNotBlank() }.distinct()

    private fun encodeTags(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun decodeTags(json: String): List<String> =
        runCatching { Json.decodeFromString(ListSerializer(String.serializer()), json) }.getOrDefault(emptyList())

    private companion object {
        const val IDS_PER_QUERY = 500
        const val HTTP_NOT_FOUND = 404
    }
}

/** A tag write asked under one account met another live account, and sent nothing more. */
internal class AccountSwitchedException : IllegalStateException("The account changed before the tag write was sent")

/**
 * Where [name] goes in [order] to sit just below [after] (at the top when null), as an index in
 * [order]; null when either is gone from it, e.g. deleted on another screen meanwhile.
 */
internal fun placeAfter(order: List<String>, name: String, after: String?): Int? {
    if (name !in order) return null
    if (after == null) return 0
    val rest = order - name
    val anchor = rest.indexOf(after)
    return if (anchor < 0) null else anchor + 1
}

/** The position PUTs that give each shown tag its own position, and the rows as the server will then hold them. */
internal class PositionSpread(val moves: List<Pair<String, Int>>, val rows: List<ConversationTagEntity>)

/**
 * Distinct, increasing positions for [shown] (in their current order) when stored ones tie. The
 * server files a name the chat PUT made, or one from before positions existed, at position 0, so an
 * account can hold several tags at 0, and tied positions sort in no fixed order.
 *
 * Each shown tag in turn moves to one position above every stored one plus the shown count. Every
 * such PUT shifts the tags already moved there down one (they sit inside the shifted range) and
 * never reaches a tag not yet moved, which stays below them, so the shown tags end up one apart, in
 * order. Renumbering to 0..n-1 instead doesn't work: each PUT's shift moves tags placed before it.
 */
internal fun spreadPositions(rows: List<ConversationTagEntity>, shown: List<ConversationTagEntity>): PositionSpread {
    val top = rows.maxOf { it.position } + shown.size
    var current = rows
    val moves = shown.map { tag ->
        current = shiftPositions(current, current.first { it.id == tag.id }, top)
        tag.tag to top
    }
    return PositionSpread(moves, current)
}

/**
 * The server's `adjustPositions` applied locally, so the optimistic order matches what the server
 * will store: [moved] takes [target], and the tags between its old and new position shift by one.
 */
internal fun shiftPositions(
    rows: List<ConversationTagEntity>,
    moved: ConversationTagEntity,
    target: Int,
): List<ConversationTagEntity> {
    val old = moved.position
    return rows.map { row ->
        when {
            row.id == moved.id -> row.copy(position = target)
            old < target && row.position > old && row.position <= target -> row.copy(position = row.position - 1)
            old > target && row.position >= target && row.position < old -> row.copy(position = row.position + 1)
            else -> row
        }
    }
}
