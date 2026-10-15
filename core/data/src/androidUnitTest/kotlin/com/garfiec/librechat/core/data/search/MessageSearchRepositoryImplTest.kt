package com.garfiec.librechat.core.data.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.db.AccountIsolationTestBase
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.network.api.MessagesApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The merge of offline and server hits against a real database and a mock server: what each
 * emission holds, and that nothing from one account reaches another's results across a switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageSearchRepositoryImplTest : AccountIsolationTestBase() {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId(accountA)))
    private val serverEnabled = MutableStateFlow<Boolean?>(true)
    private val online = MutableStateFlow(true)
    private val requests = AtomicInteger()

    /** What the mock server answers; replace per test. */
    private var respondWith: suspend () -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to remoteBody() }

    private val repository by lazy {
        val client = HttpClient(
            MockEngine {
                requests.incrementAndGet()
                val (status, body) = respondWith()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            expectSuccess = true // production's HttpResponseValidator throws on non-2xx too
            install(ContentNegotiation) { json(librechatJson) }
        }
        MessageSearchRepositoryImpl(
            searchDao = db.messageSearchDao(),
            conversationDao = db.conversationDao(),
            scanEngine = MessageScanEngine(db.messageSearchDao(), Dispatchers.Default),
            messagesApi = MessagesApi(client),
            capability = object : SearchCapabilityRepository {
                override val serverSearchEnabled = serverEnabled
                override suspend fun refresh() = Unit
            },
            connectivity = object : ConnectivityObserver {
                override val isConnected = online
            },
            activeAccountProvider = accounts,
            activity = LocalSearchActivity(),
            indexState = SearchIndexState(
                PreferenceDataStoreFactory.create(scope = storeScope) { File(tmp.root, "s.preferences_pb") },
            ),
            snippetDispatcher = Dispatchers.Default,
        )
    }

    @After
    fun stopStore() = storeScope.cancel()

    private suspend fun seed(id: String, accountId: String, text: String, conversationId: String = "conv-$accountId") {
        db.conversationDao().upsert(conversation(conversationId, accountId, title = "Title $conversationId"))
        db.messageDao().upsertAllIndexed(
            listOf(message(id, conversationId = conversationId, accountId = accountId, text = text).copy(createdAt = 10L)),
        )
    }

    private fun remoteBody(vararg ids: String = arrayOf("local-1", "remote-1")) = buildString {
        append("""{"messages":[""")
        append(
            ids.joinToString(",") { id ->
                """{"messageId":"$id","conversationId":"server-conv","text":"remote quokka $id",
                   "isCreatedByUser":true,"title":"Server title"}"""
            },
        )
        append("""],"nextCursor":null}""")
    }

    private fun collect(query: String, count: Int) = runBlocking {
        withTimeout(10_000) { repository.search(query).take(count).toList() }
    }

    /** Everything emitted within [millis]: the flow follows the account, so it never completes. */
    private suspend fun collectWithin(query: String, millis: Long = 1_500): List<MessageSearchResult> {
        val seen = mutableListOf<MessageSearchResult>()
        withTimeoutOrNull(millis) { repository.search(query).collect { seen += it } }
        return seen
    }

    @Test
    fun localHitsComeFirstThenTheServersAreMergedIn() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")

        val (first, second) = collect("quokka", 2)

        assertThat(first.hits.map { it.messageId }).containsExactly("local-1")
        assertThat(first.remoteStatus).isEqualTo(RemoteSearchStatus.Searching)
        assertThat(first.hits.single().conversationTitle).isEqualTo("Title conv-$accountA")
        // local-1 came back from both: reported once, as local. remote-1 is appended after.
        assertThat(second.hits.map { it.messageId }).containsExactly("local-1", "remote-1").inOrder()
        assertThat(second.hits.map { it.source }).containsExactly(HitSource.Local, HitSource.Remote).inOrder()
        assertThat(second.hits.last().conversationTitle).isEqualTo("Server title")
        assertThat(second.hits.last().snippet.highlights).isNotEmpty()
        assertThat(second.remoteStatus).isEqualTo(RemoteSearchStatus.Done)
    }

    @Test
    fun aServerHitMustHoldEveryWordLikeALocalOne() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka remote kiwi")

        // The server's engine matches any one word; "remote quokka remote-1" has no "kiwi".
        val (_, second) = collect("quokka kiwi", 2)

        assertThat(second.hits.map { it.messageId }).containsExactly("local-1")
        assertThat(second.remoteStatus).isEqualTo(RemoteSearchStatus.Done)
    }

    @Test
    fun aFullServerPageSaysTheServerHasMore() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")
        respondWith = { HttpStatusCode.OK to remoteBody(*Array(MessagesApi.SERVER_DEFAULT_PAGE_SIZE) { "remote-$it" }) }

        val (_, full) = collect("quokka", 2)
        assertThat(full.remoteCapped).isTrue()

        respondWith = { HttpStatusCode.OK to remoteBody("remote-1") }
        val (_, partial) = collect("quokka remote", 2)
        assertThat(partial.remoteCapped).isFalse()
    }

    @Test
    fun anOfflineSearchAsksTheServerOnceTheConnectionReturns() = runBlocking<Unit> {
        online.value = false
        seed("local-1", accountA, "local quokka")
        launch {
            delay(1_000)
            online.value = true
        }

        val (offline, searching, done) = collect("quokka", 3)

        assertThat(offline.remoteSkipped).isEqualTo(RemoteSkipReason.Offline)
        assertThat(searching.remoteSkipped).isNull()
        assertThat(searching.remoteStatus).isEqualTo(RemoteSearchStatus.Searching)
        assertThat(done.remoteStatus).isEqualTo(RemoteSearchStatus.Done)
        assertThat(done.remoteSkipped).isNull()
        assertThat(done.hits.map { it.messageId }).containsExactly("local-1", "remote-1").inOrder()
    }

    @Test
    fun serverHitsInACachedArchivedChatAreLeftOutByDefault() = runBlocking<Unit> {
        db.conversationDao().upsert(conversation("server-conv", accountA, isArchived = true))
        seed("local-1", accountA, "local quokka")

        val (_, second) = collect("quokka", 2)

        // remote-1 lives in server-conv, which the cache knows is archived.
        assertThat(second.hits.map { it.messageId }).containsExactly("local-1")
    }

    @Test
    fun aSearchBeforeTheFirstProbeWaitsForItInsteadOfCallingTheServerOff() = runBlocking<Unit> {
        serverEnabled.value = null
        seed("local-1", accountA, "local quokka")
        launch {
            delay(200)
            serverEnabled.value = true
        }

        val (first, second) = collect("quokka", 2)

        assertThat(first.remoteStatus).isEqualTo(RemoteSearchStatus.Searching)
        assertThat(first.remoteSkipped).isNull()
        assertThat(second.remoteStatus).isEqualTo(RemoteSearchStatus.Done)
    }

    @Test
    fun withServerSearchOffThereIsOneLocalEmission() = runBlocking<Unit> {
        serverEnabled.value = false
        seed("local-1", accountA, "local quokka")

        val results = collectWithin("quokka")

        assertThat(results).hasSize(1)
        assertThat(results.single().remoteStatus).isEqualTo(RemoteSearchStatus.Unavailable)
        assertThat(results.single().remoteSkipped).isEqualTo(RemoteSkipReason.ServerOff)
        assertThat(requests.get()).isEqualTo(0)
    }

    @Test
    fun aShortQueryStaysLocal() = runBlocking<Unit> {
        seed("local-1", accountA, "ox herds")

        val results = collectWithin("ox")

        assertThat(results.single().hits.map { it.messageId }).containsExactly("local-1")
        // Short queries are local by design, so there's nothing to explain.
        assertThat(results.single().remoteSkipped).isNull()
        assertThat(requests.get()).isEqualTo(0)
    }

    @Test
    fun aServerFailureKeepsTheLocalHits() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")
        respondWith = { HttpStatusCode.InternalServerError to """{"error":"meili down"}""" }

        val (_, second) = collect("quokka", 2)

        assertThat(second.remoteStatus).isEqualTo(RemoteSearchStatus.Failed)
        assertThat(second.hits.map { it.messageId }).containsExactly("local-1")
    }

    @Test
    fun anotherAccountsMessagesNeverMatch() = runBlocking<Unit> {
        serverEnabled.value = false
        seed("a-1", accountA, "shared pelican")
        seed("b-1", accountB, "shared pelican")

        val result = collect("pelican", 1).single()

        assertThat(result.accountId).isEqualTo(accountA)
        assertThat(result.hits.map { it.messageId }).containsExactly("a-1")
        assertThat(result.hits.single().accountId).isEqualTo(accountA)
    }

    @Test
    fun aServerReplyLandingAfterASwitchNeverReachesTheNewAccount() = runBlocking<Unit> {
        seed("a-1", accountA, "pelican from a")
        seed("b-1", accountB, "pelican from b")
        val release = CompletableDeferred<Unit>()
        respondWith = {
            release.await()
            HttpStatusCode.OK to remoteBody("a-remote")
        }
        // Appended on Dispatchers.Default while this thread polls it.
        val seen = CopyOnWriteArrayList<MessageSearchResult>()
        val job = launch(Dispatchers.Default) { repository.search("pelican").collect { seen += it } }
        withTimeout(10_000) { while (seen.isEmpty()) yield() }

        accounts.set(AccountId(accountB))
        withTimeout(10_000) { while (seen.none { it.accountId == accountB }) yield() }
        release.complete(Unit)
        withTimeout(10_000) { while (seen.none { it.accountId == accountB && it.remoteStatus != RemoteSearchStatus.Searching }) yield() }
        job.cancel()

        val afterSwitch = seen.dropWhile { it.accountId == accountA }
        assertThat(afterSwitch.map { it.accountId }.distinct()).containsExactly(accountB)
        assertThat(seen.flatMap { it.hits }.filter { it.accountId == accountB }.map { it.messageId })
            .doesNotContain("a-1")
        assertThat(seen.filter { it.accountId == accountA }.flatMap { it.hits }.map { it.messageId })
            .doesNotContain("a-remote")
    }

    @Test
    fun aRepeatedQueryReusesTheServersAnswerUntilTheAccountChanges() = runBlocking<Unit> {
        seed("a-1", accountA, "pelican")
        seed("b-1", accountB, "pelican")

        collect("pelican", 2)
        collect("pelican", 2)
        assertThat(requests.get()).isEqualTo(1)

        accounts.set(AccountId(accountB))
        collect("pelican", 2)
        accounts.set(AccountId(accountA))
        collect("pelican", 2)

        assertThat(requests.get()).isEqualTo(3)
    }

    @Test
    fun unsegmentedScriptsAreScannedWithProgress() = runBlocking<Unit> {
        serverEnabled.value = false
        seed("cjk", accountA, "我们的数据库很快")
        seed("other", accountA, "nothing here", conversationId = "other-conv")

        val result = collect("数据库", 1).single()

        assertThat(result.hits.map { it.messageId }).containsExactly("cjk")
        assertThat(result.scan?.scanned).isEqualTo(2)
        assertThat(result.scan?.total).isEqualTo(2)
        assertThat(result.scan?.resumeFrom).isNull()
        assertThat(result.hits.single().snippet.highlights).isNotEmpty()
    }

    @Test
    fun noAccountMeansNoResults() = runBlocking<Unit> {
        accounts.clear()

        val results = collectWithin("pelican", millis = 500)

        assertThat(results).isEmpty()
    }

    // ---- advanced options ----

    private fun collectWith(query: String, options: SearchOptions, count: Int = 1) = runBlocking {
        withTimeout(10_000) { repository.search(query, options = options).take(count).toList() }
    }

    @Test
    fun aRegexRunsLocallyAndNeverAsksTheServer() = runBlocking<Unit> {
        seed("hit", accountA, "error 404 from the proxy")
        seed("miss", accountA, "error four", conversationId = "c2")

        val result = collectWith("error [0-9]{3}", SearchOptions(regex = true)).single()
        delay(300)

        assertThat(result.hits.map { it.messageId }).containsExactly("hit")
        assertThat(result.hits.single().snippet.highlights).isNotEmpty()
        // A jump finds the text the pattern matched; the chat's find can't run a pattern.
        assertThat(result.hits.single().findText).isEqualTo("error 404")
        assertThat(result.remoteSkipped).isEqualTo(RemoteSkipReason.Regex)
        assertThat(requests.get()).isEqualTo(0)
    }

    @Test
    fun aRegexThatCanMatchNothingOnlyReportsRealMatches() = runBlocking<Unit> {
        seed("hit", accountA, "build 42 failed")
        seed("miss", accountA, "no numbers here", conversationId = "c2")

        val result = collectWith("[0-9]*", SearchOptions(regex = true)).single()

        assertThat(result.hits.map { it.messageId }).containsExactly("hit")
        assertThat(result.hits.single().findText).isEqualTo("42")
    }

    @Test
    fun aDangerousOrBrokenRegexIsReportedInsteadOfRun() = runBlocking<Unit> {
        seed("m", accountA, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab")

        val tooComplex = collectWith("(a+)+b", SearchOptions(regex = true)).single()
        val invalid = collectWith("(unclosed", SearchOptions(regex = true)).single()

        assertThat(tooComplex.regexProblem).isEqualTo(RegexProblem.TooComplex)
        assertThat(invalid.regexProblem).isEqualTo(RegexProblem.Invalid)
        assertThat(tooComplex.hits).isEmpty()
    }

    @Test
    fun matchCaseAndWholeWordRecheckTheIndexCandidates() = runBlocking<Unit> {
        serverEnabled.value = false
        seed("exact", accountA, "Kotlin flow")
        seed("lower", accountA, "kotlin flow", conversationId = "c2")
        seed("longer", accountA, "kotlinx flow", conversationId = "c3")

        val caseHits = collectWith("Kotlin", SearchOptions(matchCase = true)).single().hits.map { it.messageId }
        val wordHits = collectWith("kotlin", SearchOptions(wholeWord = true)).single().hits.map { it.messageId }

        assertThat(caseHits).containsExactly("exact")
        assertThat(wordHits).containsExactly("exact", "lower")
    }

    @Test
    fun aDateFilterSkipsTheServerWhoseHitsHaveNoDates() = runBlocking<Unit> {
        seed("m", accountA, "pelican")

        val result = collectWith("pelican", SearchOptions(since = 1L)).single()
        delay(300)

        assertThat(result.remoteSkipped).isEqualTo(RemoteSkipReason.DateFilter)
        assertThat(requests.get()).isEqualTo(0)
    }

    @Test
    fun roleFiltersApplyToServerHitsToo() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")

        val (_, merged) = collectWith("quokka", SearchOptions(role = RoleFilter.Assistant), count = 2)

        // Every server hit in the fixture is the user's, so the assistant filter drops them all.
        assertThat(merged.hits.map { it.source }).doesNotContain(HitSource.Remote)
    }

    // ---- reasoning ----

    private suspend fun seedReasoning(id: String, think: String, text: String, conversationId: String) {
        db.conversationDao().upsert(conversation(conversationId, accountA))
        val content = """[{"type":"think","think":"$think"},{"type":"text","text":"$text"}]"""
        db.messageDao().upsertAllIndexed(
            listOf(message(id, conversationId = conversationId, accountId = accountA, text = "").copy(content = content)),
        )
    }

    @Test
    fun aHitOnlyInTheReasoningIsMarked() = runBlocking<Unit> {
        serverEnabled.value = false
        seedReasoning("thought", think = "pondering wombats", text = "The answer", conversationId = "c1")
        seedReasoning("said", think = "hmm", text = "wombats are great", conversationId = "c2")

        val hits = collectWith("wombats", SearchOptions()).single().hits.associateBy { it.messageId }

        assertThat(hits.getValue("thought").inReasoning).isTrue()
        assertThat(hits.getValue("said").inReasoning).isFalse()
    }

    @Test
    fun answersOnlySkipsMatchesInTheReasoning() = runBlocking<Unit> {
        serverEnabled.value = false
        seedReasoning("thought", think = "pondering wombats", text = "The answer", conversationId = "c1")
        seedReasoning("said", think = "hmm", text = "wombats are great", conversationId = "c2")

        val hits = collectWith("wombats", SearchOptions(visibleTextOnly = true)).single().hits

        assertThat(hits.map { it.messageId }).containsExactly("said")
    }

    // ---- source ----

    @Test
    fun cachedOnlyNeverAsksTheServer() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")

        val result = collectWith("quokka", SearchOptions(source = SearchSource.Cached)).single()
        delay(300)

        assertThat(result.hits.map { it.messageId }).containsExactly("local-1")
        assertThat(result.remoteSkipped).isEqualTo(RemoteSkipReason.CachedOnly)
        assertThat(requests.get()).isEqualTo(0)
    }

    @Test
    fun serverOnlySkipsTheOfflineIndex() = runBlocking<Unit> {
        seed("local-1", accountA, "local quokka")

        val (first, second) = collectWith("quokka", SearchOptions(source = SearchSource.Server), count = 2)

        assertThat(first.hits).isEmpty()
        // local-1 comes back from the server here, so it now shows as a server hit.
        assertThat(second.hits.map { it.messageId }).containsExactly("local-1", "remote-1").inOrder()
        assertThat(second.hits.map { it.source }.distinct()).containsExactly(HitSource.Remote)
    }
}
