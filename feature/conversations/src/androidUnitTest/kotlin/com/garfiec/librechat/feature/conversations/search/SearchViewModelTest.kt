package com.garfiec.librechat.feature.conversations.search

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.search.HighlightedSnippet
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.MessageSearchRepository
import com.garfiec.librechat.core.data.search.MessageSearchResult
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.RoleFilter
import com.garfiec.librechat.core.data.search.ScanCursor
import com.garfiec.librechat.core.data.search.ScanProgress
import com.garfiec.librechat.core.data.search.SearchFilterValues
import com.garfiec.librechat.core.data.search.SearchOptions
import com.garfiec.librechat.core.data.search.SearchSort
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.feature.conversations.drawer.DrawerSearchSources
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accountA = AccountId("srv:a")
    private val accountB = AccountId("srv:b")
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(accountA))

    private var nextResult: (String) -> MessageSearchResult = { query -> result(query, accountA) }

    /** When set, answers by scan cursor instead of [nextResult]. */
    private var scanPage: ((ScanCursor?) -> MessageSearchResult)? = null
    private val searchedWith = mutableListOf<SearchOptions>()

    /** When set, the search follows this flow, so a test can land a second emission. */
    private var live: Flow<MessageSearchResult>? = null
    private val messages = object : MessageSearchRepository {
        override fun search(
            query: String,
            limit: Int,
            scanFrom: ScanCursor?,
            options: SearchOptions,
        ): Flow<MessageSearchResult> {
            searchedWith += options
            return live ?: flowOf(scanPage?.invoke(scanFrom) ?: nextResult(query))
        }

        override suspend fun filterValues() = SearchFilterValues(models = filterModels)
    }
    private var filterModels = emptyList<String>()
    private val advanced = MutableStateFlow(false)
    private val preferenceStore = mockk<PreferenceStore> {
        every { observe(AppPreferences.AdvancedSearch) } returns advanced
    }
    private val conversationRepository = mockk<ConversationRepository> {
        every { observeConversations(any()) } returns flowOf(
            Result.Success(listOf(Conversation(conversationId = "c-title", title = "Pelican facts"))),
        )
    }
    private val messageRepository = mockk<MessageRepository> {
        every { observeMessages(any()) } returns flowOf(emptyList())
        every { observeMessages("c1") } returns flowOf(
            listOf(
                Message(messageId = "p1", conversationId = "c1", text = "tell me about pelican beaks", isCreatedByUser = true),
                Message(messageId = "m1", conversationId = "c1", parentMessageId = "p1", text = "a pelican pouch holds water"),
                Message(messageId = "f1", conversationId = "c1", parentMessageId = "m1", text = "and their wings?", isCreatedByUser = true),
            ),
        )
        coEvery { getMessages(any()) } returns Result.Error(message = "offline")
    }

    private val agentRepository = mockk<AgentRepository> {
        coEvery { getAgent(any()) } returns Result.Error(message = "unknown")
        coEvery { getAgent("agent_pelican") } returns Result.Success(Agent(id = "agent_pelican", name = "Pelican Pal"))
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun hit(id: String, conversation: String, source: HitSource = HitSource.Local, account: AccountId = accountA) =
        MessageSearchHit(
            accountId = account.value, messageId = id, conversationId = conversation,
            conversationTitle = "Title $conversation", createdAt = 1L, isCreatedByUser = false, model = null,
            endpoint = null, snippet = HighlightedSnippet("pelican", listOf(0..6), false, false), source = source,
        )

    private fun result(query: String, account: AccountId) = MessageSearchResult(
        accountId = account.value,
        query = query,
        hits = listOf(
            hit("m1", "c1", account = account),
            hit("m2", "c2", account = account),
            hit("m3", "c1", account = account),
            hit("r1", "c9", HitSource.Remote, account = account),
        ),
        remoteStatus = RemoteSearchStatus.Done,
    )

    private fun viewModel(query: String = "pelican") = SearchViewModel(
        initialQuery = query,
        sources = DrawerSearchSources(
            messages = messages,
            capability = object : SearchCapabilityRepository {
                override val serverSearchEnabled = MutableStateFlow<Boolean?>(false)
                override suspend fun refresh() = Unit
            },
            titles = mockk<SearchRepository>(),
        ),
        conversationRepository = conversationRepository,
        messageRepository = messageRepository,
        agentRepository = agentRepository,
        activeAccountProvider = accounts,
        defaultDispatcher = dispatcher,
        preferenceStore = preferenceStore,
    )

    @Test
    fun `hits are grouped by conversation in arrival order, title matches first, server-only groups last`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertThat(state.groups.map { it.conversationId }).containsExactly("c-title", "c1", "c2", "c9").inOrder()
            assertThat(state.groups.first().hits).isEmpty()
            assertThat(state.groups.first().titleHighlights).containsExactly(0..6)
            assertThat(state.groups[1].hits.map { it.messageId }).containsExactly("m1", "m3").inOrder()
            assertThat(state.groups.last().fromServer).isTrue()
            assertThat(state.messageCount).isEqualTo(4)
            assertThat(state.chatCount).isEqualTo(1)
        }

    @Test
    fun `the server's conversation search only contributes titles that match`() {
        val titles = TitleMatches(
            query = "flow",
            cached = emptyList(),
            server = listOf(
                Conversation(conversationId = "c1", title = "Unrelated"), // body match, hits already listed
                Conversation(conversationId = "s-body", title = "Something else"), // body match, no hits here
                Conversation(conversationId = "s-title", title = "Flow in Kotlin"), // reordered words still match
            ),
        )
        val groups = SearchViewModel.group(listOf(hit("m1", "c1")))

        val merged = SearchViewModel.merge(accountA.value, groups, titles)

        assertThat(merged.map { it.conversationId }).containsExactly("c1", "s-title", "s-body").inOrder()
        assertThat(merged.first { it.conversationId == "c1" }.titleMatched).isFalse()
        assertThat(merged.first { it.conversationId == "s-title" }.titleHighlights).containsExactly(0..3)
        assertThat(merged.first { it.conversationId == "s-body" }.serverBodyMatch).isTrue()
    }

    @Test
    fun `a body-only server chat is dropped when its missing text can't be checked`() {
        val server = listOf(Conversation(conversationId = "s-body", title = "Something else"))
        val groups = SearchViewModel.group(listOf(hit("m1", "c1")))

        // The server's engine matches either word, so a two-word body match may hold only one.
        val twoWords = SearchViewModel.merge(accountA.value, groups, TitleMatches("kotlin flow", emptyList(), server))
        // A filter or rule the server's unreturned text can't be checked against.
        val filtered = SearchViewModel.merge(
            accountA.value,
            groups,
            TitleMatches("flow", emptyList(), server),
            SearchOptions(model = "gpt-4o"),
        )
        val dated = SearchViewModel.merge(accountA.value, groups, TitleMatches("flow", emptyList(), server), SearchOptions(since = 1L))

        assertThat(twoWords.map { it.conversationId }).containsExactly("c1")
        assertThat(filtered.map { it.conversationId }).containsExactly("c1")
        assertThat(dated.map { it.conversationId }).containsExactly("c1")
    }

    @Test
    fun `the messages tab counts body-only server chats it lists`() {
        val state = SearchUiState(
            messageCount = 2,
            groups = listOf(
                SearchResultGroup("c1", "One", listOf(hit("m1", "c1"), hit("m2", "c1")), fromServer = false),
                SearchResultGroup("s1", "Two", emptyList(), fromServer = true, serverBodyMatch = true),
            ),
        )

        assertThat(state.messageTabCount).isEqualTo(3)
    }

    @Test
    fun `a server-only group takes its date from the server's conversation`() {
        val groups = SearchViewModel.group(listOf(hit("r1", "s1", HitSource.Remote).copy(createdAt = null)))
        val server = listOf(
            Conversation(conversationId = "s1", title = "Other", updatedAt = Instant.fromEpochMilliseconds(42L)),
        )

        val merged = SearchViewModel.merge(accountA.value, groups, TitleMatches("pelican", emptyList(), server))

        assertThat(merged.single().newestAt).isEqualTo(42L)
    }

    @Test
    fun `a two-letter word being typed says one more letter widens the search`() {
        assertThat(SearchViewModel.shortPrefixOf("ki", SearchOptions.Default)).isEqualTo("ki")
        assertThat(SearchViewModel.shortPrefixOf("kit", SearchOptions.Default)).isNull()
        assertThat(SearchViewModel.shortPrefixOf("ki ", SearchOptions.Default)).isNull()
        assertThat(SearchViewModel.shortPrefixOf("ki", SearchOptions(wholeWord = true))).isNull()
    }

    @Test
    fun `title matches follow the conversation filters`() {
        val chat = Conversation(
            conversationId = "c",
            title = "Pelicans",
            model = "gpt-4o",
            updatedAt = Instant.fromEpochMilliseconds(100L),
        )

        assertThat(SearchOptions(since = 50L).admitsConversation(chat)).isTrue()
        assertThat(SearchOptions(since = 200L).admitsConversation(chat)).isFalse()
        assertThat(SearchOptions(model = "gpt-4o").admitsConversation(chat)).isTrue()
        assertThat(SearchOptions(model = "claude").admitsConversation(chat)).isFalse()
    }

    @Test
    fun `a role filter leaves title-only chats out`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.groups.map { it.conversationId }).contains("c-title")

        vm.onOptionsChange { it.copy(role = RoleFilter.User) }
        advanceUntilIdle()

        assertThat(vm.state.value.groups.map { it.conversationId }).doesNotContain("c-title")
    }

    @Test
    fun `the preview's place in its chat follows hits that land later`() = runTest(dispatcher) {
        val results = MutableStateFlow(
            MessageSearchResult(accountA.value, "pelican", listOf(hit("m1", "c1"), hit("m3", "c1")), RemoteSearchStatus.Searching),
        )
        live = results
        val vm = viewModel()
        advanceUntilIdle()
        vm.select("m3")
        advanceUntilIdle()
        assertThat(vm.state.value.preview?.count).isEqualTo(2)

        results.value = results.value.copy(
            hits = listOf(hit("m1", "c1"), hit("m3", "c1"), hit("r5", "c1", HitSource.Remote)),
            remoteStatus = RemoteSearchStatus.Done,
        )
        advanceUntilIdle()

        assertThat(vm.state.value.preview?.index).isEqualTo(1)
        assertThat(vm.state.value.preview?.count).isEqualTo(3)
    }

    @Test
    fun `selecting a conversation previews it and the list keeps both kinds of row in key order`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.selectConversation("c-title")
            advanceUntilIdle()
            assertThat(vm.state.value.chatPreview?.group?.conversationId).isEqualTo("c-title")
            assertThat(vm.state.value.selectedMessageId).isNull()

            vm.moveSelection(1) // the c1 header
            vm.moveSelection(1) // its first hit
            assertThat(vm.state.value.selectedMessageId).isEqualTo("m1")
            assertThat(vm.state.value.chatPreview).isNull()
        }

    @Test
    fun `a two-pane preview starts on the first hit and keeps a choice the user made`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.ensureSelection()
        assertThat(vm.state.value.selectedMessageId).isEqualTo("m1")

        vm.select("m2")
        vm.ensureSelection()
        assertThat(vm.state.value.selectedMessageId).isEqualTo("m2")
    }

    @Test
    fun `folding a group hides its hits from the keys until the query changes`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleCollapsed("c1")
        val entries = vm.state.value.entries
        assertThat(entries).doesNotContain(SearchEntry.Hit(vm.state.value.groups.first { it.conversationId == "c1" }.hits.first()))
        assertThat(entries).contains(SearchEntry.Conversation("c1"))

        vm.onQueryChange("pelicans")
        advanceUntilIdle()
        assertThat(vm.state.value.collapsed).isEmpty()
    }

    @Test
    fun `selecting a cached hit previews it with its parent and steps within its conversation`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.select("m1")
        advanceUntilIdle()

        val preview = vm.state.value.preview!!
        assertThat(preview.count).isEqualTo(2)
        assertThat(preview.thread.map { it.messageId }).containsExactly("p1", "m1", "f1").inOrder()
        assertThat(preview.hitIndex).isEqualTo(1)
        assertThat(preview.thread[1].snippet.text).contains("pelican pouch")
        assertThat(preview.thread[0].isUser).isTrue()
        assertThat(preview.load).isEqualTo(ThreadLoad.Ready)

        vm.stepInConversation(1)
        assertThat(vm.state.value.selectedMessageId).isEqualTo("m3")
    }

    @Test
    fun `a server hit's conversation is fetched and opened at the hit`() = runTest(dispatcher) {
        coEvery { messageRepository.getMessages("c9") } returns Result.Success(
            listOf(
                Message(messageId = "q9", conversationId = "c9", text = "ask about pelicans", isCreatedByUser = true),
                Message(messageId = "r1", conversationId = "c9", parentMessageId = "q9", text = "pelicans fish"),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.select("r1")
        assertThat(vm.state.value.preview?.load).isEqualTo(ThreadLoad.Loading)
        advanceUntilIdle()

        val preview = vm.state.value.preview!!
        assertThat(preview.load).isEqualTo(ThreadLoad.Ready)
        assertThat(preview.thread.map { it.messageId }).containsExactly("q9", "r1").inOrder()
        assertThat(preview.hitIndex).isEqualTo(1)
    }

    @Test
    fun `a server hit whose conversation can't be fetched falls back to its snippet`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.select("r1")
        advanceUntilIdle()

        val preview = vm.state.value.preview!!
        assertThat(preview.load).isEqualTo(ThreadLoad.Failed)
        assertThat(preview.thread).isEmpty()
    }

    @Test
    fun `a cached hit is read from the cache without a fetch`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.select("m1")
        advanceUntilIdle()

        coVerify(exactly = 0) { messageRepository.getMessages(any()) }
    }

    @Test
    fun `arrow keys move through every header and hit and stop at the ends`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.moveSelection(1)
        assertThat(vm.state.value.selectedConversationId).isEqualTo("c-title")
        vm.moveSelection(1)
        vm.moveSelection(1)
        assertThat(vm.state.value.selectedMessageId).isEqualTo("m1")
        repeat(10) { vm.moveSelection(1) }
        assertThat(vm.state.value.selectedMessageId).isEqualTo("r1")
    }

    @Test
    fun `an account switch clears everything and a stale hit cannot be opened`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val staleHit = vm.state.value.groups.first { it.hits.isNotEmpty() }.hits.first()

        accounts.set(accountB)
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(SearchUiState())
        assertThat(vm.openable(staleHit)).isNull()
    }

    @Test
    fun `a result tagged with another account is dropped`() = runTest(dispatcher) {
        nextResult = { query -> result(query, accountB) }

        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.searched).isFalse()
        assertThat(vm.state.value.groups).isEmpty()
    }

    @Test
    fun `a too-short query shows nothing`() = runTest(dispatcher) {
        val vm = viewModel(query = "p")
        advanceUntilIdle()

        assertThat(vm.state.value.searched).isFalse()
    }

    @Test
    fun `options reach the search only while advanced search is on`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOptionsChange { it.copy(matchCase = true, role = RoleFilter.User) }
        advanceUntilIdle()
        assertThat(searchedWith.last()).isEqualTo(SearchOptions.Default)

        advanced.value = true
        advanceUntilIdle()
        assertThat(searchedWith.last()).isEqualTo(SearchOptions(matchCase = true, role = RoleFilter.User))
        assertThat(vm.state.value.activeFilterCount).isEqualTo(1)

        advanced.value = false
        advanceUntilIdle()
        assertThat(searchedWith.last()).isEqualTo(SearchOptions.Default)
    }

    @Test
    fun `a pattern's preview marks every match of the pattern`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel(query = "pel[a-z]+")
        vm.onOptionsChange { it.copy(regex = true) }
        advanceUntilIdle()

        vm.select("m1")
        advanceUntilIdle()

        val thread = vm.state.value.preview!!.thread
        val marked = thread.flatMap { turn -> turn.snippet.highlights.map { turn.snippet.text.substring(it) } }
        assertThat(marked).containsExactly("pelican", "pelican").inOrder()
    }

    @Test
    fun `a new query rebuilds the preview's marks`() = runTest(dispatcher) {
        val vm = viewModel(query = "pelican")
        advanceUntilIdle()
        vm.select("m1")
        advanceUntilIdle()
        val before = vm.state.value.preview!!.thread[1].snippet.highlights

        vm.onQueryChange("water")
        advanceUntilIdle()

        val after = vm.state.value.preview!!.thread[1].snippet
        assertThat(after.highlights).isNotEqualTo(before)
        assertThat(after.highlights.map { after.text.substring(it) }).containsExactly("water")
    }

    @Test
    fun `an agent chat's preview names the agent`() = runTest(dispatcher) {
        nextResult = { query ->
            result(query, accountA).let { it.copy(hits = it.hits.map { hit -> hit.copy(model = "agent_pelican") }) }
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.select("m1")
        advanceUntilIdle()

        assertThat(vm.state.value.agentNames).containsExactly("agent_pelican", "Pelican Pal")
    }

    @Test
    fun `a regex is searched however short it is`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel(query = ".")
        vm.onOptionsChange { it.copy(regex = true) }
        advanceUntilIdle()

        assertThat(searchedWith.last().regex).isTrue()
    }

    @Test
    fun `an account switch resets the options too`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel()
        vm.onOptionsChange { it.copy(wholeWord = true) }
        advanceUntilIdle()

        accounts.set(accountB)
        advanceUntilIdle()

        assertThat(vm.state.value.options).isEqualTo(SearchOptions.Default)
        assertThat(vm.state.value.advancedEnabled).isTrue()
    }

    @Test
    fun `most matches ranks conversations, local and server apart`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel()
        vm.onOptionsChange { it.copy(sort = SearchSort.MostHits) }
        advanceUntilIdle()

        // c1 has two hits, c2 one, the title-only c-title none; the server-only c9 stays after the local groups.
        assertThat(vm.state.value.groups.map { it.conversationId }).containsExactly("c1", "c2", "c-title", "c9").inOrder()
        assertThat(SearchViewModelTestGroups.ranked().map { it.conversationId }).containsExactly("b", "a", "s").inOrder()
    }

    @Test
    fun `scan more adds the next page to what the scan already found`() = runTest(dispatcher) {
        val cursor = ScanCursor(beforeCreatedAt = 5L, beforeRowId = 9L)
        scanPage = { from ->
            if (from == null) {
                MessageSearchResult(
                    accountId = accountA.value, query = "数据", hits = listOf(hit("m1", "c1"), hit("m2", "c2")),
                    remoteStatus = RemoteSearchStatus.Unavailable, scan = ScanProgress(200, 500, resumeFrom = cursor),
                )
            } else {
                MessageSearchResult(
                    accountId = accountA.value, query = "数据", hits = listOf(hit("m3", "c1")),
                    remoteStatus = RemoteSearchStatus.Unavailable, scan = ScanProgress(300, 500, resumeFrom = null),
                )
            }
        }
        val vm = viewModel(query = "数据")
        advanceUntilIdle()

        vm.scanMore()
        advanceUntilIdle()

        val state = vm.state.value
        assertThat(state.orderedHits.map { it.messageId }).containsExactly("m1", "m3", "m2").inOrder()
        assertThat(state.messageCount).isEqualTo(3)
        assertThat(state.scan?.scanned).isEqualTo(500)
        assertThat(state.scan?.resumeFrom).isNull()
    }

    @Test
    fun `a slow server title search doesn't hold back the message hits`() = runTest(dispatcher) {
        val titles = mockk<SearchRepository> {
            coEvery { search(any(), any()) } coAnswers {
                delay(30_000)
                Result.Success(listOf(Conversation(conversationId = "s-title", title = "Pelican habitats")))
            }
        }
        val vm = SearchViewModel(
            initialQuery = "pelican",
            sources = DrawerSearchSources(
                messages = messages,
                capability = object : SearchCapabilityRepository {
                    override val serverSearchEnabled = MutableStateFlow<Boolean?>(true)
                    override suspend fun refresh() = Unit
                },
                titles = titles,
            ),
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            agentRepository = agentRepository,
            activeAccountProvider = accounts,
            defaultDispatcher = dispatcher,
            preferenceStore = preferenceStore,
        )
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(vm.state.value.messageCount).isEqualTo(4)

        advanceUntilIdle()
        assertThat(vm.state.value.groups.map { it.conversationId }).contains("s-title")
    }

    @Test
    fun `a single list keeps the search's order and steps through it`() = runTest(dispatcher) {
        advanced.value = true
        val vm = viewModel()
        vm.onGroupingChange(SearchGrouping.None)
        advanceUntilIdle()

        assertThat(vm.state.value.orderedHits.map { it.messageId }).containsExactly("m1", "m2", "m3", "r1").inOrder()
        repeat(3) { vm.moveSelection(1) } // the title-only c-title, then m1, then m2
        assertThat(vm.state.value.selectedMessageId).isEqualTo("m2")
    }

    @Test
    fun `a preview leaves out turns with nothing to read, but never the hit`() {
        val thread = listOf(
            Message(messageId = "u", conversationId = "c", text = "find the pelican", isCreatedByUser = true),
            Message(messageId = "tools", conversationId = "c", parentMessageId = "u", text = ""),
            Message(messageId = "a", conversationId = "c", parentMessageId = "tools", text = "a pelican"),
        )
        val marks = SearchViewModel.previewMarksFor("pelican", SearchOptions.Default)

        val turns = SearchViewModel.turnsOf(thread, hit = null, marks, SearchOptions.Default)
        assertThat(turns.map { it.messageId }).containsExactly("u", "a").inOrder()

        val asHit = SearchViewModel.turnsOf(thread, hit = hit("tools", "c"), marks, SearchOptions.Default)
        assertThat(asHit.map { it.messageId }).containsExactly("u", "tools", "a").inOrder()
    }

    @Test
    fun `a selection waits for the server before an emission without it drops it`() = runTest(dispatcher) {
        val both = listOf(hit("m1", "c1"), hit("r5", "c9", HitSource.Remote))
        val results = MutableStateFlow(MessageSearchResult(accountA.value, "pelican", both, RemoteSearchStatus.Done))
        live = results
        val vm = viewModel()
        advanceUntilIdle()
        vm.select("r5")
        advanceUntilIdle()

        // An option change searches again: the local leg lands first, without the server hit.
        results.value = results.value.copy(hits = listOf(hit("m1", "c1")), remoteStatus = RemoteSearchStatus.Searching)
        advanceUntilIdle()
        vm.ensureSelection()
        assertThat(vm.state.value.selectedMessageId).isEqualTo("r5")

        results.value = results.value.copy(hits = both, remoteStatus = RemoteSearchStatus.Done)
        advanceUntilIdle()
        assertThat(vm.state.value.selectedMessageId).isEqualTo("r5")

        results.value = results.value.copy(hits = listOf(hit("m1", "c1")), remoteStatus = RemoteSearchStatus.Done)
        advanceUntilIdle()
        assertThat(vm.state.value.selectedMessageId).isNull()
    }

    @Test
    fun `clearing the query clears the last search's notes`() = runTest(dispatcher) {
        nextResult = { query -> result(query, accountA).copy(remoteCapped = true) }
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.state.value.remoteCapped).isTrue()

        vm.onQueryChange("")
        advanceUntilIdle()

        assertThat(vm.state.value.remoteCapped).isFalse()
        assertThat(vm.state.value.searched).isFalse()
    }

    @Test
    fun `the model filter learns agent names`() = runTest(dispatcher) {
        filterModels = listOf("gpt-5", "agent_pelican")
        advanced.value = true
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.state.value.filterValues.models).containsExactly("gpt-5", "agent_pelican")
        assertThat(vm.state.value.agentNames).containsExactly("agent_pelican", "Pelican Pal")
    }
}

private object SearchViewModelTestGroups {
    private fun group(id: String, hits: Int, server: Boolean) = SearchResultGroup(
        conversationId = id,
        title = id,
        hits = List(hits) {
            MessageSearchHit(
                accountId = "a", messageId = "$id$it", conversationId = id, conversationTitle = id, createdAt = 0L,
                isCreatedByUser = false, model = null, endpoint = null,
                snippet = HighlightedSnippet("x", emptyList(), false, false),
                source = if (server) HitSource.Remote else HitSource.Local,
            )
        },
        fromServer = server,
    )

    fun ranked() = SearchViewModel.byMostHits(listOf(group("a", 1, false), group("s", 5, true), group("b", 3, false)))
}
