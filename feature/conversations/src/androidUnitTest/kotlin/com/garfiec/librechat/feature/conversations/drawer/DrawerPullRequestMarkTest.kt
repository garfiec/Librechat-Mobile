package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.pullrequest.PullRequestIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Instant

class DrawerPullRequestMarkTest {

    private val pr =
        ConversationPullRequest(number = 482, title = "Fix crash", state = "open", mergeable = "clean", checks = "running")
    private val code = Conversation(conversationId = "c1", codeEnvironmentMode = "attached")

    @Test
    fun aCodeChatWithAnAnswerGetsTheMark() {
        val mark = code.toDrawerDisplayData(null, emptyMap(), "", pullRequests = mapOf("c1" to pr)).pullRequest
        assertThat(mark?.pullRequest?.number).isEqualTo(482)
        assertThat(mark?.presentation?.icon).isEqualTo(PullRequestIcon.OPEN)
    }

    @Test
    fun aRunningRowShowsNoMark() {
        val data = code.toDrawerDisplayData(null, emptyMap(), "", runningIds = setOf("c1"), pullRequests = mapOf("c1" to pr))
        assertThat(data.pullRequest).isNull()
    }

    @Test
    fun aKnownOrdinaryChatShowsNoMark() {
        val ordinary = Conversation(conversationId = "c1", codeDecisionListed = true)
        assertThat(ordinary.toDrawerDisplayData(null, emptyMap(), "", pullRequests = mapOf("c1" to pr)).pullRequest).isNull()
    }

    @Test
    fun lookupsSkipOrdinaryRunningAndFreshRows() {
        val now = Instant.fromEpochMilliseconds(100_000)
        val rows = listOf(
            code,
            Conversation(conversationId = "unknown"),
            Conversation(conversationId = "ordinary", codeDecisionListed = true),
            Conversation(conversationId = "running", codeEnvironmentMode = "attached"),
            Conversation(conversationId = "fresh", codeEnvironmentMode = "attached"),
            Conversation(conversationId = "stale", codeEnvironmentMode = "attached"),
        )
        val ids = pullRequestLookupIds(
            conversations = rows,
            runningIds = setOf("running"),
            answeredAt = mapOf("fresh" to now, "stale" to Instant.fromEpochMilliseconds(0)),
            staleBefore = Instant.fromEpochMilliseconds(40_000),
        )
        assertThat(ids).containsExactly("c1", "unknown", "stale").inOrder()
    }
}
