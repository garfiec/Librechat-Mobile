package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.identity.flatMapAccountOrEmpty
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
 */
class TagRepositoryImpl(
    private val tagsApi: TagsApi,
    private val tagDao: ConversationTagDao,
    private val conversationsApi: ConversationsApi,
    private val conversationDao: ConversationDao,
    private val activeAccountProvider: ActiveAccountProvider,
    private val changes: TagChangeSignal,
) : TagRepository {

    /**
     * Serializes the read-then-write of a chat's tags: two quick toggles would otherwise both read
     * the same set and the second PUT would drop the first change.
     */
    private val chatTagWrites = Mutex()

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

    override suspend fun refreshTags(): Result<Unit> = safeApiCall {
        val account = activeAccountProvider.currentAccountId() ?: return@safeApiCall
        val tags = tagsApi.getTags().filterNot { it.tag.isNullOrBlank() }
        // Scoped replace: only this account's tag rows are swapped, never another account's.
        tagDao.replaceAllForAccount(
            account.value,
            tags.map { it.toEntity().copy(accountId = account.value) },
        )
    }

    override suspend fun getConversationTags(conversationId: String): Result<List<String>> = safeApiCall {
        fetchChatTags(conversationId, activeAccountProvider.currentAccountId()?.value)
    }

    override suspend fun setConversationTag(
        conversationId: String,
        tag: String,
        attached: Boolean,
    ): Result<List<String>> {
        val name = tag.trim()
        if (name.isEmpty()) return Result.Error(IllegalArgumentException("Blank tag name"), "Blank tag name")
        return safeApiCall {
            val stored = chatTagWrites.withLock {
                writeChatTag(conversationId, name, attached, activeAccountProvider.currentAccountId()?.value)
            }
            afterChange()
            stored
        }
    }

    override suspend fun toggleFavorite(
        conversationId: String,
        currentTags: List<String>,
    ): Result<List<String>> = setConversationTag(conversationId, SAVED_TAG, attached = SAVED_TAG !in currentTags)

    override suspend fun createTag(
        name: String,
        description: String?,
        addToConversationId: String?,
    ): Result<ConversationTag> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Error(IllegalArgumentException("Blank tag name"), "Blank tag name")
        return safeApiCall {
            val accountId = activeAccountProvider.currentAccountId()?.value
            val created = tagsApi.createTag(
                tag = trimmed,
                description = description?.takeIf { it.isNotBlank() },
                addToConversation = addToConversationId != null,
                conversationId = addToConversationId,
            )
            if (addToConversationId != null) {
                // The server attaches only a NEW name: an existing one comes back early, unattached.
                // The read-then-write covers both, and caches the chat's tags either way.
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
            afterChange()
            created
        }
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
        return safeApiCall {
            val accountId = activeAccountProvider.currentAccountId()?.value
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
    }

    override suspend fun deleteTag(name: String): Result<Unit> = safeApiCall {
        val accountId = activeAccountProvider.currentAccountId()?.value
        tagsApi.deleteTag(name)
        if (accountId != null) {
            rewriteCachedChatTags(accountId) { tags -> tags - name }
        }
        changes.emit(TagEvent.Deleted(name))
        afterChange()
    }

    override suspend fun reorderTag(name: String, toIndex: Int): Result<Unit> {
        val accountId = activeAccountProvider.currentAccountId()?.value
            ?: return Result.Error(IllegalStateException("No resolved account"), "No resolved account")
        val snapshot = tagDao.getTagsForAccount(accountId)
        val visible = snapshot.filter { row -> row.tag.isNotBlank() && row.tag != SAVED_TAG }
        val from = visible.indexOfFirst { it.tag == name }
        if (from < 0 || toIndex !in visible.indices) {
            return Result.Error(IllegalArgumentException("No bookmark $name at $toIndex"), "Bookmark not found")
        }
        if (from == toIndex) return Result.Success(Unit)
        // The server moves the tag to a stored `position` and shifts the ones in between, and stored
        // positions needn't be 0..n-1 (and the list here hides Saved), so send the position of the
        // tag now in the target slot rather than the index.
        val moved = visible[from]
        val target = visible[toIndex].position
        tagDao.upsertAll(shiftPositions(snapshot, moved, target))
        return when (val result = safeApiCall { tagsApi.updateTag(currentTag = name, position = target) }) {
            is Result.Success -> {
                refreshTags()
                changes.bump()
                Result.Success(Unit)
            }
            is Result.Error -> {
                tagDao.replaceAllForAccount(accountId, snapshot)
                result
            }
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun clearCache() {
        // Scope to the active account when known; logout's scoped DELETE is the authoritative purge.
        activeAccountProvider.currentAccountId()?.let { tagDao.deleteAllForAccount(it.value) }
    }

    /**
     * The chat's tags as the server has them (the list endpoint omits them, so the cache can't be
     * trusted). Caches the whole server row, tags included.
     */
    private suspend fun fetchChatTags(conversationId: String, accountId: String?): List<String> {
        val conversation = conversationsApi.getConversation(conversationId)
        val tags = conversation.tags.cleaned()
        if (accountId != null) {
            conversationDao.upsert(conversation.copy(tags = tags).toEntity().copy(accountId = accountId))
        }
        return tags
    }

    /** Must run under [chatTagWrites]. [knownToExist] skips the create for a name just created. */
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
        if (attached && !knownToExist && !isKnownTag(accountId, name)) {
            // Created here, not by the chat PUT: that one would file a new name at position 0.
            tagsApi.createTag(tag = name)
        }
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

    private suspend fun isKnownTag(accountId: String?, name: String): Boolean =
        accountId != null && tagDao.getTagsForAccount(accountId).any { it.tag == name }

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

    private fun List<String>.cleaned(): List<String> = map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun encodeTags(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)
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
