package com.garfiec.librechat.core.data.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.data.db.AccountIsolationTestBase
import com.garfiec.librechat.core.data.db.dao.MessageSearchDao
import com.garfiec.librechat.core.data.prefetch.PrefetchConditions
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIndexBackfillSessionTaskTest : AccountIsolationTestBase() {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state by lazy {
        SearchIndexState(PreferenceDataStoreFactory.create(scope = storeScope) { File(tmp.root, "s.preferences_pb") })
    }

    /** Prefetch is off and the network is metered: neither may hold the index back. */
    private val conditions = MutableStateFlow(
        PrefetchConditions(
            enabled = false,
            appAvailable = true,
            networkAllowed = false,
            powerAvailable = true,
            appIdle = true,
            connected = false,
        ),
    )
    private val activity = LocalSearchActivity()

    private fun task(dao: MessageSearchDao = db.messageSearchDao()) = SearchIndexBackfillSessionTask(
        dao = dao,
        state = state,
        maintenance = SearchIndexMaintenance(db),
        window = object : SearchIndexWindow {
            override val isOpen = conditions.map { it.allowsIndexing() }
        },
        activity = activity,
        startDelay = Duration.ZERO,
        batchPause = Duration.ZERO,
    )

    @After
    fun stopStore() = storeScope.cancel()

    private suspend fun seedUnindexed(count: Int, accountId: String? = accountA) {
        db.messageDao().upsertAll(
            (1..count).map {
                message("m$it", conversationId = "c", accountId = accountA, text = "seeded row $it").copy(accountId = accountId)
            },
        )
    }

    private fun indexedCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM message_search").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun indexesEveryClaimedRowAndMarksItselfDone() = runTest {
        seedUnindexed(230)

        task().run()

        assertThat(indexedCount()).isEqualTo(230)
        assertThat(state.isBackfillDone()).isTrue()
        assertThat(state.backfillCursor()).isNull()
    }

    @Test
    fun resumesBelowTheSavedCursorAndRepairsTheRest() = runTest {
        seedUnindexed(50)
        val rowids = db.openHelper.writableDatabase.query("SELECT rowid FROM messages ORDER BY rowid").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        // A previous run already covered everything at and above the 21st row.
        state.saveBackfillCursor(rowids[20])

        task().run()

        // The backfill indexed only the 20 rows below the cursor, so the repair found 20 of 50 and
        // filled in the rest. Had the cursor been ignored, the counts would already have matched
        // and the repair would have recorded nothing.
        assertThat(indexedCount()).isEqualTo(50)
        assertThat(state.repairSettledGap()).isEqualTo(0)
    }

    @Test
    fun aDoneIndexOnlyRepairs() = runTest {
        seedUnindexed(5)
        state.markBackfillDone()

        task().run()

        assertThat(indexedCount()).isEqualTo(5)
        assertThat(state.repairSettledGap()).isEqualTo(0)
    }

    @Test
    fun repairRemembersCountsItCannotFix() = runTest {
        seedUnindexed(3, accountId = null)
        state.markBackfillDone()

        task().run()

        assertThat(indexedCount()).isEqualTo(0)
        assertThat(state.repairSettledGap()).isEqualTo(3)
    }

    @Test
    fun newIndexedMessagesDoNotRerunARepairThatSettledOnUnclaimedRows() = runTest {
        seedUnindexed(3, accountId = null)
        state.markBackfillDone()
        task().run()

        db.messageDao().upsertAllIndexed(listOf(message("fresh", conversationId = "c2", accountId = accountA)))
        val dao = spyk(db.messageSearchDao())
        task(dao).run()

        coVerify(exactly = 0) { dao.repairBatch(any()) }
    }

    @Test
    fun anUnindexedRowAfterASettleRunsTheRepair() = runTest {
        seedUnindexed(3, accountId = null)
        state.markBackfillDone()
        task().run()

        db.messageDao().upsertAll(listOf(message("stray", conversationId = "c2", accountId = accountA)))
        task().run()

        assertThat(indexedCount()).isEqualTo(1)
        assertThat(state.repairSettledGap()).isEqualTo(3)
    }

    @Test
    fun waitsWhileALocalSearchRuns() = runBlocking {
        seedUnindexed(10)
        val release = MutableStateFlow(false)
        val search = launch(Dispatchers.Default) { activity.track { release.first { it } } }
        withTimeout(5_000) { while (activity.inFlight.value == 0) yield() }

        val run = launch(Dispatchers.Default) { task().run() }
        Thread.sleep(300)
        assertThat(indexedCount()).isEqualTo(0)

        release.value = true
        withTimeout(10_000) { run.join() }
        search.join()
        assertThat(indexedCount()).isEqualTo(10)
    }

    @Test
    fun overlappingRunsCollapseIntoOne() = runBlocking {
        seedUnindexed(10)
        conditions.value = conditions.value.copy(appIdle = false)
        val task = task()
        val first = launch(Dispatchers.Default) { task.run() }
        Thread.sleep(100)

        task.run() // returns at once: the first run holds the lock

        conditions.value = conditions.value.copy(appIdle = true)
        withTimeout(10_000) { first.join() }
        assertThat(indexedCount()).isEqualTo(10)
    }

    @Test
    fun anOlderIndexFormatIsRebuiltOnce() = runTest {
        // Side rows written in the previous shape: no reasoning offset recorded.
        db.messageDao().upsertAllIndexed(
            (1..5).map { message("m$it", conversationId = "c", accountId = accountA, text = "seeded row $it") },
        )
        db.openHelper.writableDatabase.execSQL("UPDATE message_search SET searchText = 'stale shape'")
        assertThat(state.indexFormat()).isEqualTo(1)

        task().run()

        assertThat(state.indexFormat()).isEqualTo(SearchIndexState.CURRENT_FORMAT)
        assertThat(indexedCount()).isEqualTo(5)
        val stale = db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH 'stale'").use { it.moveToFirst(); it.getInt(0) }
        assertThat(stale).isEqualTo(0)
    }
}
