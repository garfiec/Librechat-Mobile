package com.garfiec.librechat.core.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The message search index against the real schema: what each write path indexes, under which
 * account, and how much work it does. Assertions count rows, segments and statements, never time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageSearchIndexTest : AccountIsolationTestBase() {

    private val messageDao get() = db.messageDao()
    private val searchDao get() = db.messageSearchDao()
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    private suspend fun hits(accountId: String, query: String): List<String> {
        val plan = FtsQuery.plan(query) as FtsQueryPlan.Match
        return searchDao.search(accountId, plan.expression, limit = 50).map { it.messageId }
    }

    private fun indexed(
        id: String,
        accountId: String?,
        text: String? = null,
        content: String? = null,
        conversationId: String = "conv",
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = message(id, conversationId = conversationId, accountId = accountId ?: "", text = text)
        .copy(accountId = accountId, content = content, createdAt = createdAt, updatedAt = updatedAt)

    // ---- write paths ----

    @Test
    fun upsertAllIndexedIndexesUnderTheMessagesOwnAccount() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("mA", accountA, text = "quokka facts")))
        messageDao.upsertAllIndexed(listOf(indexed("mB", accountB, text = "quokka photos", conversationId = "convB")))

        assertThat(hits(accountA, "quokka")).containsExactly("mA")
        assertThat(hits(accountB, "quokka")).containsExactly("mB")
        assertThat(sideAccount("mA")).isEqualTo(accountA)
    }

    @Test
    fun unclaimedLegacyRowsAreNeverIndexed() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("legacy", accountId = null, text = "quokka")))

        assertThat(count("SELECT COUNT(*) FROM message_search")).isEqualTo(0)
        assertThat(hits(accountA, "quokka")).isEmpty()
    }

    @Test
    fun agentRepliesAreIndexedFromTheirContentParts() = runTest {
        val content = """[{"type":"think","think":"pondering wombats"},{"type":"text","text":"Here is the answer"},""" +
            """{"type":"steer","steer":"mention platypus"},{"type":"tool_call","tool_call":{"id":"t","name":"x","args":"secretarg"}}]"""
        messageDao.upsertAllIndexed(listOf(indexed("agent", accountA, text = "", content = content)))

        assertThat(hits(accountA, "wombats")).containsExactly("agent")
        assertThat(hits(accountA, "answer")).containsExactly("agent")
        assertThat(hits(accountA, "platypus")).containsExactly("agent")
        assertThat(hits(accountA, "secretarg")).isEmpty()
    }

    @Test
    fun anUnchangedRefetchDoesNotTouchTheIndex() = runTest {
        val rows = (1..20).map { indexed("m$it", accountA, text = "message number $it", updatedAt = 5L) }
        messageDao.upsertAllIndexed(rows)
        val segmentsBefore = count("SELECT COUNT(*) FROM messages_fts_segdir")
        val docsBefore = count("SELECT COUNT(*) FROM messages_fts")
        installWriteProbe("message_search")

        messageDao.upsertAllIndexed(rows)

        assertThat(probeCount()).isEqualTo(0)
        assertThat(count("SELECT COUNT(*) FROM messages_fts_segdir")).isEqualTo(segmentsBefore)
        assertThat(count("SELECT COUNT(*) FROM messages_fts")).isEqualTo(docsBefore)
    }

    @Test
    fun aChangedMessageIsReindexedAndItsOldTextDropsOut() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "original aardvark", updatedAt = 1L)))

        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "edited badger", updatedAt = 2L)))

        assertThat(hits(accountA, "aardvark")).isEmpty()
        assertThat(hits(accountA, "badger")).containsExactly("m1")
    }

    @Test
    fun replaceAllForConversationDropsTermsOnlyTheOldRowsHad() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("old", accountA, text = "obsolete capybara")))

        messageDao.replaceAllForConversation("conv", accountA, listOf(indexed("new", accountA, text = "fresh capybara")))

        assertThat(hits(accountA, "obsolete")).isEmpty()
        assertThat(hits(accountA, "capybara")).containsExactly("new")
    }

    @Test
    fun replaceAllForConversationWithUnchangedRowsDoesNotTouchTheIndex() = runTest {
        val rows = (1..20).map { indexed("m$it", accountA, text = "message number $it", updatedAt = 5L) }
        messageDao.upsertAllIndexed(rows)
        installWriteProbe("message_search")

        messageDao.replaceAllForConversation("conv", accountA, rows)

        assertThat(probeCount()).isEqualTo(0)
        assertThat(hits(accountA, "number")).hasSize(20)
    }

    @Test
    fun updateTextIndexesALegacyThinkingBlockAsReasoning() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "plain")))

        messageDao.updateText("m1", ":::thinking\npondering tapirs\n:::\nThe answer", accountA)

        val (searchText, visibleLength) = sql.query(
            "SELECT searchText, visibleLength FROM message_search WHERE messageId = 'm1'",
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0) to (if (cursor.isNull(1)) null else cursor.getInt(1))
        }
        assertThat(searchText).isEqualTo("The answer\npondering tapirs")
        assertThat(visibleLength).isEqualTo("The answer".length)
        assertThat(hits(accountA, "tapirs")).containsExactly("m1")
    }

    @Test
    fun updateTextReindexesAnIndexedRow() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "before ocelot")))

        messageDao.updateText("m1", "after lynx", accountA)

        assertThat(hits(accountA, "ocelot")).isEmpty()
        assertThat(hits(accountA, "lynx")).containsExactly("m1")
    }

    @Test
    fun updateTextIndexesARowThatNeverHadASideRow() = runTest {
        messageDao.upsert(indexed("m1", accountA, text = "unindexed"))

        messageDao.updateText("m1", "edited narwhal", accountA)

        assertThat(hits(accountA, "narwhal")).containsExactly("m1")
        assertThat(sideAccount("m1")).isEqualTo(accountA)
    }

    @Test
    fun updateTextUnderAnotherAccountTouchesNothing() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "private gecko")))

        messageDao.updateText("m1", "hijacked", accountB)

        assertThat(hits(accountA, "gecko")).containsExactly("m1")
        assertThat(hits(accountB, "hijacked")).isEmpty()
    }

    @Test
    fun deletingAnAccountsMessagesUnindexesOnlyThatAccount() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("mA", accountA, text = "shared heron")))
        messageDao.upsertAllIndexed(listOf(indexed("mB", accountB, text = "shared heron", conversationId = "convB")))

        messageDao.deleteAllForAccount(accountA)

        assertThat(hits(accountA, "heron")).isEmpty()
        assertThat(hits(accountB, "heron")).containsExactly("mB")
        assertThat(count("SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH 'heron'")).isEqualTo(1)
    }

    @Test
    fun pruningConversationsUnindexesThem() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "pruned ibis", conversationId = "c1")))
        messageDao.upsertAllIndexed(listOf(indexed("m2", accountA, text = "kept ibis", conversationId = "c2")))

        messageDao.deleteForConversations(accountA, listOf("c1"))

        assertThat(hits(accountA, "ibis")).containsExactly("m2")
    }

    // ---- reads ----

    @Test
    fun searchIsNewestFirstAndLimited() = runTest {
        messageDao.upsertAllIndexed((1..5).map { indexed("m$it", accountA, text = "tapir $it", createdAt = it.toLong()) })

        val plan = FtsQuery.plan("tapir") as FtsQueryPlan.Match
        val rows = searchDao.search(accountA, plan.expression, limit = 3)

        assertThat(rows.map { it.messageId }).containsExactly("m5", "m4", "m3").inOrder()
    }

    @Test
    fun prefixMatchesWhileTheLastWordIsBeingTyped() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "kotlin coroutines")))

        assertThat(hits(accountA, "corou")).containsExactly("m1")
        assertThat(hits(accountA, "kotlin corou")).containsExactly("m1")
        assertThat(hits(accountA, "corou ")).isEmpty()
    }

    @Test
    fun hostileInputNeverReachesTheParserAsSyntax() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "alpha beta OR gamma")))
        val inputs = listOf(
            "alpha OR", "NOT alpha", "\"alpha", "alpha*beta", "searchText:alpha",
            "alpha NEAR/2 beta", "(alpha", "-alpha", "alpha ^",
        )

        inputs.forEach { input ->
            val plan = FtsQuery.plan(input)
            if (plan is FtsQueryPlan.Match) searchDao.search(accountA, plan.expression, limit = 5)
        }
        assertThat(hits(accountA, "alpha OR")).containsExactly("m1")
    }

    @Test
    fun searchQueryWalksTheAccountIndexWithoutASort() {
        val plan = explain(
            "SELECT rowid AS rowId, messageId FROM message_search WHERE accountId = 'a' " +
                "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH '\"x\"') " +
                "ORDER BY createdAt DESC LIMIT 20",
        )
        assertThat(plan).contains("index_message_search_accountId_createdAt")
        assertThat(plan).doesNotContain("TEMP B-TREE")
    }

    /** The default search leaves archived chats out, so this is the plain search's real plan. */
    @Test
    fun archiveExcludingSearchStillWalksTheIndexWithoutASort() {
        val plan = explain(
            "SELECT rowid AS rowId, messageId FROM message_search WHERE accountId = 'a' " +
                "AND +rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH '\"x\"') " +
                "AND createdAt >= 0 AND (NULL IS NULL OR isCreatedByUser = NULL) " +
                "AND (1 = 0 OR conversationId NOT IN " +
                "(SELECT conversationId FROM conversations WHERE accountId = 'a' AND isArchived = 1)) " +
                "ORDER BY createdAt DESC LIMIT 20",
        )
        assertThat(plan).contains("index_message_search_accountId_createdAt")
        assertThat(plan).doesNotContain("TEMP B-TREE")
    }

    @Test
    fun scanPageIsIndexOrderedWithoutASort() {
        val plan = explain(
            "SELECT rowid, messageId FROM message_search WHERE accountId = 'a' " +
                "AND (createdAt < 5 OR (createdAt = 5 AND rowid < 9)) ORDER BY createdAt DESC, rowid DESC LIMIT 200",
        )
        assertThat(plan).contains("index_message_search_accountId_createdAt")
        assertThat(plan).doesNotContain("TEMP B-TREE")
    }

    // ---- advanced filters ----

    @Test
    fun filteredSearchNarrowsByRoleModelDateAndArchive() = runTest {
        db.conversationDao().upsert(conversation("open", accountA))
        db.conversationDao().upsert(conversation("shelved", accountA, isArchived = true))
        messageDao.upsertAllIndexed(
            listOf(
                indexed("user-old", accountA, text = "heron", conversationId = "open", createdAt = 1L)
                    .copy(isCreatedByUser = true),
                indexed("bot-new", accountA, text = "heron", conversationId = "open", createdAt = 5L)
                    .copy(isCreatedByUser = false, model = "gpt-5"),
                indexed("user-new", accountA, text = "heron", conversationId = "open", createdAt = 6L)
                    .copy(isCreatedByUser = true),
                indexed("archived", accountA, text = "heron", conversationId = "shelved", createdAt = 7L),
            ),
        )
        suspend fun ids(isUser: Boolean? = null, model: String? = null, since: Long = 0L, excludeArchived: Boolean = false) =
            searchDao.searchFiltered(accountA, "\"heron\"", since, isUser, model, null, excludeArchived, 50).map { it.messageId }

        assertThat(ids()).containsExactly("archived", "user-new", "bot-new", "user-old").inOrder()
        assertThat(ids(isUser = true)).containsExactly("user-new", "user-old").inOrder()
        assertThat(ids(model = "gpt-5")).containsExactly("bot-new")
        assertThat(ids(since = 5L, excludeArchived = true)).containsExactly("user-new", "bot-new").inOrder()
    }

    @Test
    fun filteredSearchStaysAccountScoped() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("mA", accountA, text = "heron")))
        messageDao.upsertAllIndexed(listOf(indexed("mB", accountB, text = "heron", conversationId = "convB")))

        val rows = searchDao.searchFiltered(accountA, "\"heron\"", 0L, null, null, null, true, 50)

        assertThat(rows.map { it.messageId }).containsExactly("mA")
    }

    // ---- backfill and repair ----

    @Test
    fun indexBatchWalksEveryRowOnceWithoutWritingMessages() = runTest {
        // Rows cached before the index existed: written raw, no side rows.
        messageDao.upsertAll((1..250).map { indexed("m$it", if (it % 50 == 0) null else accountA, text = "row $it llama") })
        installWriteProbe("messages")

        var cursor = Long.MAX_VALUE
        var batches = 0
        while (true) {
            batches++
            cursor = searchDao.indexBatch(cursor, limit = 100) ?: break
        }

        // 245 claimed rows: three batches with rows, one empty.
        assertThat(batches).isEqualTo(4)
        assertThat(probeCount()).isEqualTo(0)
        assertThat(count("SELECT COUNT(*) FROM message_search")).isEqualTo(245)
        assertThat(hits(accountA, "llama")).hasSize(50)
    }

    @Test
    fun indexBatchSkipsRowsThatAreAlreadyIndexed() = runTest {
        messageDao.upsertAllIndexed(listOf(indexed("m1", accountA, text = "already okapi")))
        val docsBefore = count("SELECT COUNT(*) FROM messages_fts")

        searchDao.indexBatch(Long.MAX_VALUE, limit = 100)

        assertThat(count("SELECT COUNT(*) FROM messages_fts")).isEqualTo(docsBefore)
    }

    @Test
    fun repairIndexesARowClaimedAfterTheBackfillPassedIt() = runTest {
        messageDao.upsert(indexed("legacy", accountId = null, text = "late jackal"))
        while (searchDao.indexBatch(Long.MAX_VALUE, limit = 100) != null) Unit
        assertThat(hits(accountA, "jackal")).isEmpty()
        sql.execSQL("UPDATE messages SET accountId = '$accountA' WHERE messageId = 'legacy'")

        val read = searchDao.repairBatch(limit = 500)

        assertThat(read).isEqualTo(1)
        assertThat(hits(accountA, "jackal")).containsExactly("legacy")
        assertThat(searchDao.repairBatch(limit = 500)).isEqualTo(0)
    }

    // ---- helpers ----

    private fun sideAccount(messageId: String): String? =
        sql.query("SELECT accountId FROM message_search WHERE messageId = ?", arrayOf(messageId)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun count(query: String): Int = sql.query(query).use { if (it.moveToFirst()) it.getInt(0) else -1 }

    private fun explain(query: String): String = sql.query("EXPLAIN QUERY PLAN $query").use { cursor ->
        buildString { while (cursor.moveToNext()) appendLine(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
    }

    /** Counts every insert, update and delete on [table] from here on. */
    private fun installWriteProbe(table: String) {
        sql.execSQL("CREATE TABLE IF NOT EXISTS write_probe (n INTEGER)")
        listOf("INSERT", "UPDATE", "DELETE").forEach { op ->
            sql.execSQL("CREATE TRIGGER probe_${table}_$op AFTER $op ON $table BEGIN INSERT INTO write_probe VALUES (1); END")
        }
    }

    private fun probeCount(): Int = count("SELECT COUNT(*) FROM write_probe")
}
