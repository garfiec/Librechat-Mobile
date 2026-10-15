package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import com.garfiec.librechat.core.data.search.MessageSearchRepository
import com.garfiec.librechat.core.data.search.MessageSearchResult
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.feature.conversations.search.searchNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** What the drawer's search reads besides the cached conversation list. */
class DrawerSearchSources(
    val messages: MessageSearchRepository,
    val capability: SearchCapabilityRepository,
    /** The server's conversation search (`/api/convos?search=`): title or any message matched. */
    val titles: SearchRepository,
) {
    /**
     * The server's conversation matches, up to [maxResults], or null when the server can't search
     * (off, unknown, failed).
     */
    suspend fun remoteTitles(query: String, maxResults: Int = SearchRepository.ONE_PAGE): List<Conversation>? {
        if (capability.serverSearchEnabled.first() != true) return null
        return (titles.search(query, maxResults) as? Result.Success)?.data
    }

    /**
     * The drawer's Messages section for a stream of typed queries. Debounced like the title filter,
     * and keyed on the active account twice over: the repository re-runs per account, and a result
     * tagged with an account that is no longer active is dropped here, so a reply landing after a
     * switch never reaches the drawer.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun messageSearch(queries: Flow<String>, accounts: ActiveAccountProvider): Flow<DrawerMessageSearch> =
        queries
            .map { it.trim() }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (FtsQuery.plan(query) == FtsQueryPlan.Empty) {
                    flowOf(null)
                } else {
                    messages.search(query, limit = MESSAGE_LIMIT)
                }
            }
            .combine(accounts.state) { result, _ ->
                result?.takeIf { it.accountId == accounts.currentAccountId()?.value }
            }
            .map { result -> result?.toDrawerMessageSearch() ?: DrawerMessageSearch.Idle }
            // Emit at once: the drawer's state combines this flow, so waiting out the debounce
            // would hold the whole drawer back for that long at launch.
            .onStart { emit(DrawerMessageSearch.Idle) }
            .distinctUntilChanged()

    private fun MessageSearchResult.toDrawerMessageSearch() = DrawerMessageSearch(
        preview = hits.take(DrawerMessageSearch.PREVIEW_COUNT),
        totalCount = hits.size,
        countCapped = hits.size >= MESSAGE_LIMIT,
        searched = true,
        searchingServer = remoteStatus == RemoteSearchStatus.Searching,
        note = searchNote(remoteStatus, remoteSkipped),
    )

    companion object {
        /** Matches the title filter's debounce, so both sections settle together. */
        const val DEBOUNCE_MS = 300L

        /** The drawer shows a preview; this only bounds the count it reports. */
        const val MESSAGE_LIMIT = 50
    }
}
