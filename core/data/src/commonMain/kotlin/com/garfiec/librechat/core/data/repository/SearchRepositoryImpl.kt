package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.network.api.ConversationsApi

class SearchRepositoryImpl(
    private val conversationsApi: ConversationsApi,
) : SearchRepository {

    override suspend fun search(query: String, maxResults: Int): Result<List<Conversation>> {
        val found = ArrayList<Conversation>()
        var cursor: String? = null
        while (found.size < maxResults) {
            val limit = (maxResults - found.size).coerceAtMost(SearchRepository.MAX_PAGE)
            val page = safeApiCall { conversationsApi.getConversations(cursor = cursor, limit = limit, search = query) }
            if (page !is Result.Success) {
                return when {
                    found.isNotEmpty() -> Result.Success(found)
                    page is Result.Error -> page
                    else -> Result.Error(message = "Search returned no result")
                }
            }
            found += page.data.conversations
            cursor = page.data.nextCursor
            if (cursor == null || page.data.conversations.isEmpty()) break
        }
        return Result.Success(found.take(maxResults))
    }
}
