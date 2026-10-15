package com.garfiec.librechat.core.data.db

import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import com.garfiec.librechat.core.data.search.SearchIndexMaintenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Timings for the search index against the budgets it prints, and whether a search waits on a
 * write, on a real device or emulator: seeds realistic widths (user ~200 B, assistant 2–50 KB with `content` JSON) and
 * measures in both journal modes. Results go to logcat (tag `SearchBench`) and to
 * `files/search-bench.txt` in the test app's data dir.
 *
 * Off by default: it takes minutes and writes ~100 MB. Run it with
 * `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.searchBench=true`
 * (add `.searchBenchRows=100000` for the stress size). Budgets are reported, not asserted: an
 * emulator's timings are a guide, and a red build over a slow host would teach nothing.
 */
@RunWith(AndroidJUnit4::class)
class SearchIndexBenchmark {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val report = StringBuilder()

    @Test
    fun benchmark() = runBlocking {
        assumeTrue("set searchBench=true to run", args.getString("searchBench") == "true")
        val rows = args.getString("searchBenchRows")?.toIntOrNull() ?: DEFAULT_ROWS
        for (mode in listOf(RoomDatabase.JournalMode.TRUNCATE, RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)) {
            run(mode, rows)
        }
        File(context.filesDir, "search-bench.txt").writeText(report.toString())
        Log.i(TAG, "report written to ${File(context.filesDir, "search-bench.txt")}")
    }

    private suspend fun run(mode: RoomDatabase.JournalMode, rows: Int) {
        val name = "search-bench-${mode.name.lowercase()}.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, LibreChatDatabase::class.java, name).setJournalMode(mode).build()
        try {
            line("== $mode, $rows messages")
            val seed = measureTime { seed(db, rows) }
            line("seed (indexed writes)          ${seed.inWholeMilliseconds} ms")
            queries(db)
            replaceDuringSearch(db)
            backfillBatch(db)
            purge(db)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun seed(db: LibreChatDatabase, rows: Int) {
        val random = Random(SEED)
        rows.chunkedRanges(SEED_BATCH).forEach { range ->
            db.messageDao().upsertAllIndexed(range.map { message(it, random, account = if (it % 10 == 0) OTHER else ACCOUNT) })
        }
    }

    private suspend fun queries(db: LibreChatDatabase) {
        val dao = db.messageSearchDao()
        for (term in listOf("pelican", "kotlin flow", "the", "fl", "zyzzyva")) {
            val plan = FtsQuery.plan(term) as? FtsQueryPlan.Match ?: continue
            val times = (1..QUERY_RUNS).map { measureTime { dao.search(ACCOUNT, plan.expression, LIMIT) } }
            line("query %-14s p50 %4d ms  p95 %4d ms  (budget p95 < 50 ms at 30k)".format("\"$term\"", times.p(0.5), times.p(0.95)))
        }
    }

    /** Does a search wait for a 300-message replace in this journal mode? */
    private suspend fun replaceDuringSearch(db: LibreChatDatabase) = coroutineScope {
        val random = Random(SEED + 1)
        val thread = (0 until REPLACE_SIZE).map { message(it, random, account = ACCOUNT, conversation = "replace-me") }
        val plan = FtsQuery.plan("pelican") as FtsQueryPlan.Match
        val replace = async(Dispatchers.IO) {
            measureTime { db.messageDao().replaceAllForConversation("replace-me", ACCOUNT, thread) }
        }
        val search = async(Dispatchers.IO) {
            measureTime { db.messageSearchDao().search(ACCOUNT, plan.expression, LIMIT) }
        }
        line("replace 300 msgs               ${replace.await().inWholeMilliseconds} ms  (budget < 150 ms)")
        line("search during that replace    ${search.await().inWholeMilliseconds} ms")
    }

    private suspend fun backfillBatch(db: LibreChatDatabase) {
        db.openHelper.writableDatabase.execSQL("DELETE FROM message_search WHERE rowid % 3 = 0")
        val batch = measureTime { db.messageSearchDao().indexBatch(Long.MAX_VALUE, limit = 100) }
        line("backfill batch (100)           ${batch.inWholeMilliseconds} ms  (budget < 50 ms)")
    }

    private suspend fun purge(db: LibreChatDatabase) {
        val maintenance = SearchIndexMaintenance(db)
        val other = measureTime { db.messageDao().deleteAllForAccount(OTHER) }
        line("purge 10% (per-row triggers)   ${other.inWholeMilliseconds} ms")
        val fast = measureTime { maintenance.deleteAccountMessages(ACCOUNT) }
        line("purge rest (drop + rebuild)    ${fast.inWholeMilliseconds} ms  (budget < 3 s for 30k)")
        val merge = measureTime { maintenance.mergeUntilDone() }
        line("merge until done               ${merge.inWholeMilliseconds} ms")
    }

    private fun message(i: Int, random: Random, account: String, conversation: String = "c${i / 40}"): MessageEntity {
        val user = i % 2 == 0
        val body = if (user) words(random, 30) else words(random, 300 + random.nextInt(6_000))
        val content = if (user) null else """[{"type":"think","think":"${words(random, 40)}"},{"type":"text","text":"$body"}]"""
        return MessageEntity(
            messageId = "$conversation-$i-$account", conversationId = conversation, parentMessageId = null, sender = null,
            text = if (user) body else "", content = content, isCreatedByUser = user, model = "gpt-5", endpoint = "openAI",
            iconURL = null, finishReason = null, tokenCount = null, feedback = null, files = null, attachments = null,
            metadata = null, createdAt = i.toLong(), updatedAt = i.toLong(), accountId = account,
        )
    }

    private fun words(random: Random, count: Int): String =
        (0 until count).joinToString(" ") { VOCABULARY[random.nextInt(VOCABULARY.size)] }

    private fun line(text: String) {
        Log.i(TAG, text)
        report.appendLine(text)
    }

    private fun List<Duration>.p(quantile: Double): Long =
        sorted()[((size - 1) * quantile).toInt()].inWholeMilliseconds

    private fun Int.chunkedRanges(size: Int): List<IntRange> = (0 until this step size).map { it until minOf(this, it + size) }

    private companion object {
        const val TAG = "SearchBench"
        const val DEFAULT_ROWS = 30_000
        const val SEED_BATCH = 300
        const val QUERY_RUNS = 20
        const val LIMIT = 200
        const val REPLACE_SIZE = 300
        const val SEED = 42
        const val ACCOUNT = "srv:bench"
        const val OTHER = "srv:other"
        val VOCABULARY = listOf(
            "the", "a", "of", "and", "to", "kotlin", "flow", "coroutine", "debounce", "state", "compose", "android",
            "room", "query", "index", "search", "message", "server", "client", "network", "cache", "pelican", "heron",
            "function", "value", "list", "map", "error", "result", "build", "gradle", "test", "fold", "screen",
        )
    }
}
