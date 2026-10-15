package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.MessageSearchRepository
import com.garfiec.librechat.core.data.search.MessageSearchResult
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.ScanCursor
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.feature.conversations.search.SearchNote
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerSearchSourcesTest {

    private val accountA = AccountId("srv:a")
    private val accountB = AccountId("srv:b")
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(accountA))
    private val serverEnabled = MutableStateFlow<Boolean?>(true)
    private val titles = mockk<SearchRepository>()

    /** Results the fake repository emits per query; a SharedFlow so a test controls timing. */
    private val results = MutableSharedFlow<MessageSearchResult>(replay = 1)
    private val searched = mutableListOf<String>()
    private val pageSizes = mutableListOf<Int>()

    private val messages = object : MessageSearchRepository {
        override fun search(
            query: String,
            limit: Int,
            scanFrom: ScanCursor?,
            options: SearchOptions,
            remotePageSize: Int,
        ): Flow<MessageSearchResult> {
            searched += query
            pageSizes += remotePageSize
            return results
        }
    }

    private val sources = DrawerSearchSources(
        messages = messages,
        capability = object : SearchCapabilityRepository {
            override val serverSearchEnabled = serverEnabled
            override suspend fun refresh() = Unit
        },
        titles = titles,
    )

    private fun hit(id: String, account: AccountId) = MessageSearchHit(
        accountId = account.value, messageId = id, conversationId = "c", conversationTitle = "T",
        createdAt = 0L, isCreatedByUser = true, model = null, endpoint = null,
        snippet = HighlightedSnippet("text", emptyList(), truncatedStart = false, truncatedEnd = false),
        source = HitSource.Local,
    )

    private fun result(account: AccountId, count: Int, status: RemoteSearchStatus) = MessageSearchResult(
        accountId = account.value,
        query = "pelican",
        hits = (1..count).map { hit("m$it", account) },
        remoteStatus = status,
    )

    @Test
    fun `hits arrive after the debounce, previewed and counted`() = runTest {
        val queries = MutableStateFlow("")
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(queries, accounts).collect { seen += it } }
        results.emit(result(accountA, 8, RemoteSearchStatus.Done))

        queries.value = "pelican"
        advanceTimeBy(DrawerSearchSources.DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(searched).isEmpty()
        advanceTimeBy(2)
        runCurrent()

        val latest = seen.last()
        assertThat(searched).containsExactly("pelican")
        // The drawer shows a preview, so the server isn't asked for its whole page.
        assertThat(pageSizes).containsExactly(DrawerSearchSources.MESSAGE_LIMIT)
        assertThat(latest.preview).hasSize(DrawerMessageSearch.PREVIEW_COUNT)
        assertThat(latest.totalCount).isEqualTo(8)
        assertThat(latest.searched).isTrue()
        assertThat(latest.note).isNull()
    }

    @Test
    fun `a capped server answer marks the count as a lower bound`() = runTest {
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(flowOf("pelican"), accounts).collect { seen += it } }
        results.emit(result(accountA, 8, RemoteSearchStatus.Done).copy(remoteCapped = true))
        advanceTimeBy(DrawerSearchSources.DEBOUNCE_MS + 1)
        runCurrent()

        assertThat(seen.last().countCapped).isTrue()
    }

    @Test
    fun `only cached results caption when the server could not search`() = runTest {
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(flowOf("pelican"), accounts).collect { seen += it } }
        results.emit(result(accountA, 1, RemoteSearchStatus.Failed))
        advanceTimeBy(DrawerSearchSources.DEBOUNCE_MS + 1)
        runCurrent()

        assertThat(seen.last().note).isEqualTo(SearchNote.Failed)
    }

    @Test
    fun `a result for an account that is no longer active is dropped`() = runTest {
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(flowOf("pelican"), accounts).collect { seen += it } }
        results.emit(result(accountA, 3, RemoteSearchStatus.Done))
        advanceTimeBy(DrawerSearchSources.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(seen.last().totalCount).isEqualTo(3)

        accounts.set(accountB)
        runCurrent()

        assertThat(seen.last()).isEqualTo(DrawerMessageSearch.Idle)
    }

    @Test
    fun `too-short queries never reach the repository`() = runTest {
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(flowOf("a"), accounts).collect { seen += it } }
        advanceTimeBy(DrawerSearchSources.DEBOUNCE_MS + 1)
        runCurrent()

        assertThat(searched).isEmpty()
        assertThat(seen.last()).isEqualTo(DrawerMessageSearch.Idle)
    }

    @Test
    fun `server titles are asked for only when the server can search`() = runTest {
        coEvery { titles.search("alpha") } returns Result.Success(listOf(Conversation(conversationId = "s1")))

        assertThat(sources.remoteTitles("alpha")?.map { it.conversationId }).containsExactly("s1")

        serverEnabled.value = false
        assertThat(sources.remoteTitles("alpha")).isNull()
        coVerify(exactly = 1) { titles.search("alpha") }
    }

    @Test
    fun `the idle state is there before the debounce`() = runTest {
        val seen = mutableListOf<DrawerMessageSearch>()
        backgroundScope.launch { sources.messageSearch(MutableStateFlow(""), accounts).collect { seen += it } }
        runCurrent()

        assertThat(seen).containsExactly(DrawerMessageSearch.Idle)
    }
}
