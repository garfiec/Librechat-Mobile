package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.TagRepository

/**
 * Refreshes conversation tags from the server when a session starts so the drawer has
 * up-to-date tag counts. Fires in parallel with other session tasks, the role fetch among them,
 * so it waits for the role before deciding. Skipped when bookmarks aren't available
 * ([BookmarksGate]): every tag route needs BOOKMARKS USE and would only answer 403.
 */
class RefreshTagsSessionTask(
    private val tagRepository: TagRepository,
    private val bookmarksGate: BookmarksGate,
) : SessionTask {
    override suspend fun run() {
        if (bookmarksGate.awaitEnabled()) tagRepository.refreshTags()
    }
}
