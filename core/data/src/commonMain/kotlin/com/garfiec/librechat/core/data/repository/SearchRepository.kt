package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation

interface SearchRepository {
    /**
     * The server's conversation search (`/api/convos?search=`): every conversation whose title **or
     * any message** matched, newest first, with nothing to say which. Pages are followed until
     * [maxResults] conversations or the last page; a page failing after the first ends the walk
     * with what came before it.
     */
    suspend fun search(query: String, maxResults: Int = ONE_PAGE): Result<List<Conversation>>

    companion object {
        /** The server's default page: enough for a caller that shows only a few titles. */
        const val ONE_PAGE: Int = 25

        /**
         * Every match the server can return: its search engine stops at 1000 hits
         * (`MEILI_SEARCH_LIMIT`), and the web sidebar pages through all of them.
         */
        const val ALL_MATCHES: Int = 1000

        /** The server's largest page (`MAX_PAGE_LIMIT`); it clamps a larger `limit` to this. */
        const val MAX_PAGE: Int = 100
    }
}
