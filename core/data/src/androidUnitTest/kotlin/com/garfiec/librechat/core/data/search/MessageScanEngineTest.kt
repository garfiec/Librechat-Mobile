package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.data.db.AccountIsolationTestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageScanEngineTest : AccountIsolationTestBase() {

    private val time = TestTimeSource()
    private val engine by lazy { MessageScanEngine(db.messageSearchDao(), Dispatchers.Unconfined, time) }

    private suspend fun seed(count: Int, accountId: String = accountA, text: (Int) -> String) {
        db.messageDao().upsertAllIndexed(
            (1..count).map {
                message("$accountId-m$it", conversationId = "c-$accountId", accountId = accountId, text = text(it))
                    .copy(createdAt = it.toLong())
            },
        )
    }

    @Test
    fun findsTextTheTokenizerCannotSplit() = runTest {
        seed(3) { if (it == 2) "我们的数据库很快" else "nothing here" }

        val result = engine.scan(accountA, TextMatcher.literal("数据库"))

        assertThat(result.hits.map { it.row.messageId }).containsExactly("$accountA-m2")
        assertThat(result.hits.single().firstMatch).isEqualTo(3..5)
        assertThat(result.next).isNull()
        assertThat(result.scanned).isEqualTo(3)
    }

    @Test
    fun onlyScansTheAccountItWasAskedFor() = runTest {
        seed(2) { "数据库 A" }
        seed(2, accountB) { "数据库 B" }

        val result = engine.scan(accountA, TextMatcher.literal("数据库"))

        assertThat(result.hits.map { it.row.messageId }).containsExactly("$accountA-m2", "$accountA-m1").inOrder()
    }

    @Test
    fun stopsAtTheMatchCapAndResumesWithoutRepeats() = runTest {
        seed(450) { "hit $it" }
        val budget = ScanBudget(maxMatches = 200)

        val first = engine.scan(accountA, TextMatcher.literal("hit"), budget = budget)
        val second = engine.scan(accountA, TextMatcher.literal("hit"), from = first.next!!, budget = budget)
        val third = engine.scan(accountA, TextMatcher.literal("hit"), from = second.next!!, budget = budget)

        assertThat(first.hits).hasSize(200)
        assertThat(second.hits).hasSize(200)
        assertThat(third.hits).hasSize(50)
        assertThat(third.next).isNull()
        val all = (first.hits + second.hits + third.hits).map { it.row.messageId }
        assertThat(all).containsNoDuplicates()
        assertThat(all.first()).isEqualTo("$accountA-m450")
    }

    @Test
    fun stopsAtTheDeadlineBetweenRows() = runTest {
        seed(300) { "row $it" }
        var calls = 0
        val slow = TextMatcher { text ->
            calls++
            time += 100.milliseconds
            if (text.contains("row")) 0..2 else null
        }

        val result = engine.scan(accountA, slow, budget = ScanBudget(maxMatches = 1_000, maxDuration = 1.seconds))

        assertThat(calls).isEqualTo(10)
        assertThat(result.scanned).isEqualTo(10)
        assertThat(result.next).isNotNull()
    }

    @Test
    fun matchesOnlyTheCappedStartOfAHugeRow() = runTest {
        seed(1) { "a".repeat(70_000) + "needle" }

        val result = engine.scan(accountA, TextMatcher.literal("needle"), budget = ScanBudget(maxRowChars = 64_000))

        assertThat(result.hits).isEmpty()
    }
}
