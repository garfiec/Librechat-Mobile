package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.ConversationRepository

/**
 * Pulls the server's pinned chats when a session starts, so the drawer's Pinned section holds pins
 * older than the loaded list pages and drops pins removed elsewhere.
 */
class SyncPinnedSessionTask(
    private val conversationRepository: ConversationRepository,
) : SessionTask {
    override suspend fun run() {
        conversationRepository.syncPinnedFromServer()
    }
}
