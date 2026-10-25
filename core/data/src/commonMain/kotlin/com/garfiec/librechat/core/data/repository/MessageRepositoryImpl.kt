package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.identity.flatMapAccountOrEmpty
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.mapper.feedbackColumnValue
import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.data.mapper.toModels
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.BranchMessageRequest
import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import com.garfiec.librechat.core.network.api.MessagesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MessageRepositoryImpl(
    private val messagesApi: MessagesApi,
    private val messageDao: MessageDao,
    private val activeAccountProvider: ActiveAccountProvider,
    private val roster: AccountRoster,
    private val dispatcher: CoroutineDispatcher,
) : MessageRepository {

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        activeAccountProvider.flatMapAccountOrEmpty(emptyList<Message>()) { account ->
            messageDao.observeMessagesForAccount(conversationId, account.value)
                .map { entities -> entities.toModels() }
        }.flowOn(dispatcher)

    override suspend fun getMessages(conversationId: String): Result<List<Message>> {
        val result = safeApiCall {
            // Capture identity before the network suspend so an in-flight account switch can't
            // mis-attribute these rows; skip caching when unresolved rather than stamping a null orphan.
            val accountId = activeAccountProvider.currentAccountId()?.value
            val messages = messagesApi.getMessages(conversationId)
            if (accountId != null) {
                messageDao.upsertAllIndexed(messages.entitiesFor(accountId))
            }
            messages
        }

        // On network failure, fall back to cached messages for the active account only.
        if (result is Result.Error) {
            val account = activeAccountProvider.currentAccountId()
            val cached = account
                ?.let { messageDao.observeMessagesForAccount(conversationId, it.value).first() }
                ?: emptyList()
            if (cached.isNotEmpty()) {
                Logger.d { "Using cached messages for $conversationId (network unavailable)" }
                return Result.Success(cached.toModels())
            }
        }
        return result
    }

    override suspend fun cacheMessages(messages: List<Message>, originAccount: AccountId?) {
        if (messages.isEmpty()) return
        // Origin-capture: stamp the account that started the stream (threaded from send time), so a
        // finalize landing after a switch attributes to it — not the live active account. Skip when
        // unresolved (warming / logged out) or when the origin account was removed since capture: a
        // null-stamped row is invisible to every account-filtered read and unreapable by the scoped
        // logout purge, and resurrecting a removed account's rows would leak purged data.
        val accountId = resolveWriteAccountId(originAccount, activeAccountProvider, roster) ?: return
        messageDao.upsertAllIndexed(messages.entitiesFor(accountId))
    }

    override suspend fun refreshMessages(
        conversationId: String,
        originAccount: AccountId?,
    ): Result<List<Message>> {
        // Origin-capture: a deferred caller may reach here after a switch. The GET rides the LIVE
        // snapshot, so firing it once the origin is no longer active would carry this conversation id
        // to another account's server under its bearer. Serve the cache instead — a routine skip.
        if (!originTransportAllowed(originAccount, activeAccountProvider)) {
            val account = resolveWriteAccountId(originAccount, activeAccountProvider, roster)
            val cached = account
                ?.let { messageDao.observeMessagesForAccount(conversationId, it).first() }
                ?: emptyList()
            return if (cached.isNotEmpty()) {
                Result.Success(cached.toModels())
            } else {
                Result.Error(IllegalStateException("Skipped message refresh for a non-active account"))
            }
        }
        return safeApiCall {
            val accountId = resolveWriteAccountId(originAccount, activeAccountProvider, roster)
            val messages = messagesApi.getMessages(conversationId)
            if (accountId != null) {
                // Full replace: delete stale cache then insert fresh server data (delete leg scoped
                // to account). NonCancellable so a cancelled caller cannot abandon the thread between
                // the delete and the insert legs — the transaction would roll back, but this keeps the
                // "one whole thread per transaction" contract true rather than merely usually true.
                withContext(NonCancellable) {
                    messageDao.replaceAllForConversation(
                        conversationId,
                        accountId,
                        messages.entitiesFor(accountId),
                    )
                }
            }
            messages
        }
    }

    override suspend fun updateFeedback(
        conversationId: String,
        messageId: String,
        feedback: MinimalFeedback?,
    ): Result<Unit> {
        return safeApiCall {
            val accountId = activeAccountProvider.currentAccountId()?.value
            messagesApi.updateFeedback(conversationId, messageId, feedback)
            // Update local cache, scoped to the active account; skip when unresolved.
            // The column is Feedback JSON, so encode through the mapper that reads it back.
            if (accountId != null) {
                messageDao.updateFeedback(messageId, feedbackColumnValue(feedback), accountId)
            }
        }
    }

    override suspend fun updateMessageText(
        conversationId: String,
        messageId: String,
        text: String,
    ): Result<Unit> {
        return safeApiCall {
            val accountId = activeAccountProvider.currentAccountId()?.value
            messagesApi.updateMessage(conversationId, messageId, UpdateMessageRequest(text = text))
            if (accountId != null) messageDao.updateText(messageId, text, accountId)
        }
    }

    override suspend fun updateMessageContentPart(
        conversationId: String,
        messageId: String,
        index: Int,
        text: String,
    ): Result<Unit> {
        return safeApiCall {
            val accountId = activeAccountProvider.currentAccountId()?.value
            messagesApi.updateMessage(
                conversationId,
                messageId,
                UpdateMessageRequest(text = text, index = index),
            )
            // The PUT answers a sanitized subset of the message that omits `content` entirely, so
            // the cached row has to be rewritten here rather than from the response. Read-modify-
            // write through the mapper (not a SQL column update) because the column is the encoded
            // part list, and `upsertReindexed` rather than `upsertAllIndexed` because this write
            // leaves `updatedAt` alone — see that method for what the dedupe would cost.
            if (accountId != null) {
                val cached = messageDao.getByIdForAccount(messageId, accountId)?.toModel()
                val parts = cached?.content
                if (cached != null && parts != null && index in parts.indices) {
                    messageDao.upsertReindexed(
                        cached.copy(content = parts.withEditedPart(index, text)).toEntity().copy(accountId = accountId),
                    )
                }
            }
        }
    }

    override suspend fun branchMessage(
        conversationId: String,
        messageId: String,
        agentId: String?,
    ): Result<Message> {
        return safeApiCall {
            messagesApi.branchMessage(
                BranchMessageRequest(
                    conversationId = conversationId,
                    messageId = messageId,
                    agentId = agentId,
                ),
            )
        }
    }

    // Maps server messages to entities stamped with the account captured at request time.
    private fun List<Message>.entitiesFor(accountId: String) =
        map { it.toEntity().copy(accountId = accountId) }

    /**
     * The edited part list, mirroring what the server stored: it SPREADS the new text over the
     * existing part (`{...currentPart, [type]: text}`) rather than replacing it, so the part keeps
     * its other fields. Writing the text into the field the part's own type names is what makes a
     * THINK edit land in `think` and not in `text`.
     *
     * Known local-only loss: a text part persisted in the `{ value, annotations }` form (the
     * Assistants thread sync) is normalized to a plain string by `FlexibleTextSerializer`, which
     * drops the annotations on re-encode. The server preserves them; the cache does not, until the
     * next fetch re-reads the row.
     */
    private fun List<MessageContentPart>.withEditedPart(index: Int, text: String) =
        mapIndexed { i, part ->
            when {
                i != index -> part
                part.type == ContentType.THINK -> part.copy(think = text)
                else -> part.copy(text = text)
            }
        }
}
