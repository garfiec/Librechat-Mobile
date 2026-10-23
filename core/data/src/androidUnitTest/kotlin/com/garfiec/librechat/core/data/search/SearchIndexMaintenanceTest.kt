package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.data.db.AccountIsolationTestBase
import com.garfiec.librechat.core.data.mapper.IndexedText
import com.garfiec.librechat.core.data.mapper.searchTextOf
import com.garfiec.librechat.core.data.repository.AccountDataPurger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The index operations that run with Room's sync triggers dropped, the indexed-text split, and
 * the oldest-first query. Counts rows and matches, never time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIndexMaintenanceTest : AccountIsolationTestBase() {

    private val maintenance by lazy { SearchIndexMaintenance(db) }
    private val sql get() = db.openHelper.writableDatabase

    private fun count(query: String): Int = sql.query(query).use { if (it.moveToFirst()) it.getInt(0) else -1 }
    private fun ftsHits(term: String) = count("SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH '$term'")
    private fun triggerCount() =
        count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'room_fts_content_sync_messages_fts_%'")

    private suspend fun seed(prefix: String, accountId: String, count: Int, word: String) {
        db.messageDao().upsertAllIndexed(
            (1..count).map { message("$prefix$it", conversationId = "c-$accountId", accountId = accountId, text = "$word $it") },
        )
    }

    @Test
    fun theTriggerListMatchesTheExportedSchema() {
        val dir = File("schemas/com.garfiec.librechat.core.data.db.LibreChatDatabase")
        val latest = dir.listFiles()!!.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        val entities = JSONObject(File(dir, "$latest.json").readText()).getJSONObject("database").getJSONArray("entities")
        val fts = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == "messages_fts" }
        val exported = fts.getJSONArray("contentSyncTriggers").let { array -> (0 until array.length()).map { array.getString(it) } }

        assertThat(SearchIndexMaintenance.SYNC_TRIGGERS).containsExactlyElementsIn(exported)
    }

    @Test
    fun resetEmptiesTheIndexAndKeepsItSyncing() = runTest {
        seed("m", accountA, 50, "heron")

        maintenance.resetIndex()

        assertThat(count("SELECT COUNT(*) FROM message_search")).isEqualTo(0)
        assertThat(ftsHits("heron")).isEqualTo(0)
        assertThat(triggerCount()).isEqualTo(4)
        seed("n", accountA, 1, "egret")
        assertThat(ftsHits("egret")).isEqualTo(1)
    }

    @Test
    fun fastPurgeRemovesOneAccountAndReindexesTheRest() = runTest {
        seed("a", accountA, 30, "heron")
        seed("b", accountB, 5, "heron")

        maintenance.deleteAccountMessages(accountA)

        assertThat(count("SELECT COUNT(*) FROM messages WHERE accountId = '$accountA'")).isEqualTo(0)
        assertThat(count("SELECT COUNT(*) FROM message_search")).isEqualTo(5)
        assertThat(ftsHits("heron")).isEqualTo(5)
        assertThat(triggerCount()).isEqualTo(4)
        db.messageDao().deleteAllForAccount(accountB)
        assertThat(ftsHits("heron")).isEqualTo(0)
    }

    @Test
    fun thePurgerTakesTheFastPathOnlyForTheBulkOfTheIndex() = runTest {
        val purger = AccountDataPurger(
            conversationDao = db.conversationDao(),
            messageDao = db.messageDao(),
            draftDao = db.draftDao(),
            tagDao = db.conversationTagDao(),
            prefetchWatermarkDao = db.prefetchWatermarkDao(),
            projectOrderDao = db.projectOrderDao(),
            ioDispatcher = Dispatchers.Unconfined,
            searchIndexMaintenance = maintenance,
        )
        seed("a", accountA, 2_100, "heron")
        seed("b", accountB, 10, "heron")

        purger.purge(AccountId(accountA))

        assertThat(count("SELECT COUNT(*) FROM messages")).isEqualTo(10)
        assertThat(ftsHits("heron")).isEqualTo(10)
    }

    @Test
    fun aPurgeRebuildsOnlyWhenTheAccountHoldsMostOfALargeIndex() = runTest {
        seed("a", accountA, 2_100, "heron")
        seed("b", accountB, 2_200, "heron")
        seed("c", "acct-c", 10, "heron")

        // B holds more than A, so A goes row by row; B then holds most of what is left.
        assertThat(maintenance.purgeAccountMessages(accountA)).isEqualTo(SearchIndexMaintenance.PurgePath.PerRow)
        assertThat(maintenance.purgeAccountMessages(accountB)).isEqualTo(SearchIndexMaintenance.PurgePath.Rebuild)
        assertThat(maintenance.purgeAccountMessages("acct-c")).isEqualTo(SearchIndexMaintenance.PurgePath.PerRow)
        assertThat(count("SELECT COUNT(*) FROM messages")).isEqualTo(0)
        assertThat(ftsHits("heron")).isEqualTo(0)
    }

    // ---- indexed text ----

    @Test
    fun reasoningGoesAfterTheVisibleTextWithItsOffset() {
        val content = """[{"type":"think","think":"pondering wombats"},{"type":"text","text":"The answer"},""" +
            """{"type":"steer","steer":"be brief"}]"""

        val indexed = searchTextOf(text = "", contentJson = content)

        assertThat(indexed.text).isEqualTo("The answer be brief\npondering wombats")
        assertThat(indexed.visibleLength).isEqualTo("The answer be brief".length)
    }

    @Test
    fun noReasoningMeansNoOffset() {
        assertThat(searchTextOf("plain", null)).isEqualTo(IndexedText("plain", null))
        val thinkOnly = searchTextOf("", """[{"type":"think","think":"only thoughts"}]""")
        assertThat(thinkOnly.text).isEqualTo("only thoughts")
        assertThat(thinkOnly.visibleLength).isEqualTo(0)
    }

    @Test
    fun oldestFirstWalksTheIndexForwards() = runTest {
        db.messageDao().upsertAllIndexed(
            (1..5).map { message("m$it", conversationId = "c", accountId = accountA, text = "tapir").copy(createdAt = it.toLong()) },
        )

        val rows = db.messageSearchDao().searchOldest(accountA, "\"tapir\"", limit = 3)

        assertThat(rows.map { it.messageId }).containsExactly("m1", "m2", "m3").inOrder()
        val plan = sql.query(
            "EXPLAIN QUERY PLAN SELECT rowid FROM message_search WHERE accountId = 'a' AND +rowid IN " +
                "(SELECT rowid FROM messages_fts WHERE messages_fts MATCH '\"x\"') ORDER BY createdAt ASC LIMIT 3",
        ).use { c -> buildString { while (c.moveToNext()) appendLine(c.getString(c.getColumnIndexOrThrow("detail"))) } }
        assertThat(plan).doesNotContain("TEMP B-TREE")
    }
}
