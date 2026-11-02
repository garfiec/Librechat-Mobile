package com.garfiec.librechat.core.model.pullrequest

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.isCodeConversation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PullRequestPresentationTest {

    private fun pr(
        state: String = "open",
        isDraft: Boolean = false,
        mergeable: String = "clean",
        checks: String = "running",
        provider: String? = null,
        url: String = "https://github.com/o/r/pull/482",
    ) = ConversationPullRequest(
        number = 482,
        title = "Fix crash",
        url = url,
        provider = provider,
        additions = 86,
        deletions = 12,
        state = state,
        isDraft = isDraft,
        mergeable = mergeable,
        checks = checks,
    )

    @Test
    fun openCleanIsGreenWithTheChecksDot() {
        val view = presentPullRequest(pr())
        assertEquals(PullRequestIcon.OPEN, view.icon)
        assertEquals(PullRequestTone.SUCCESS, view.iconTone)
        assertEquals(PullRequestTone.WARNING, view.dotTone)
        assertEquals(PullRequestStateLabel.OPEN, view.state)
        assertEquals(PullRequestTone.SUCCESS, view.stateTone)
        assertEquals(PullRequestChecksLabel.RUNNING, view.checks)
        assertEquals(PullRequestProvider.GITHUB, view.provider)
    }

    @Test
    fun openWithUnknownMergeabilityHasANeutralGlyphButOpenState() {
        val view = presentPullRequest(pr(mergeable = "unknown"))
        assertEquals(PullRequestTone.NEUTRAL, view.iconTone)
        assertEquals(PullRequestTone.SUCCESS, view.stateTone)
    }

    @Test
    fun conflictsAreRed() {
        val view = presentPullRequest(pr(mergeable = "conflicting"))
        assertEquals(PullRequestTone.ERROR, view.iconTone)
        assertEquals(PullRequestStateLabel.CONFLICTS, view.state)
        assertEquals(PullRequestTone.ERROR, view.stateTone)
    }

    @Test
    fun draftWinsOverConflicts() {
        val view = presentPullRequest(pr(isDraft = true, mergeable = "conflicting"))
        assertEquals(PullRequestIcon.DRAFT, view.icon)
        assertEquals(PullRequestTone.NEUTRAL, view.iconTone)
        assertEquals(PullRequestStateLabel.DRAFT, view.state)
    }

    @Test
    fun mergedIsPurpleAndHasNoDot() {
        val view = presentPullRequest(pr(state = "merged", checks = "passing"))
        assertEquals(PullRequestIcon.MERGED, view.icon)
        assertEquals(PullRequestTone.MERGED, view.iconTone)
        assertNull(view.dotTone)
        assertEquals(PullRequestChecksLabel.PASSING, view.checks)
    }

    @Test
    fun closedIsNeutralWithNoDot() {
        val view = presentPullRequest(pr(state = "closed", checks = "failing"))
        assertEquals(PullRequestIcon.CLOSED, view.icon)
        assertEquals(PullRequestTone.NEUTRAL, view.iconTone)
        assertNull(view.dotTone)
    }

    @Test
    fun noChecksShowsNoDot() {
        assertNull(presentPullRequest(pr(checks = "none")).dotTone)
    }

    @Test
    fun gitlabIsAMergeRequest() {
        val view = presentPullRequest(pr(provider = "gitlab"))
        assertEquals(PullRequestProvider.GITLAB, view.provider)
        assertTrue(view.isMergeRequest)
        assertFalse(presentPullRequest(pr(provider = "bitbucket")).isMergeRequest)
    }

    @Test
    fun refetchIntervalFollowsUpstream() {
        assertEquals(60_000L, pullRequestRefetchIntervalMs(null))
        assertEquals(20_000L, pullRequestRefetchIntervalMs(pr(checks = "running")))
        assertEquals(20_000L, pullRequestRefetchIntervalMs(pr(state = "merged", checks = "running")))
        assertNull(pullRequestRefetchIntervalMs(pr(state = "merged", checks = "passing")))
        assertEquals(20_000L, pullRequestRefetchIntervalMs(pr(checks = "passing", mergeable = "unknown")))
        assertEquals(60_000L, pullRequestRefetchIntervalMs(pr(checks = "passing", mergeable = "clean")))
    }

    @Test
    fun onlyAnAbsoluteWebUrlOpens() {
        assertEquals("https://github.com/o/r/pull/482", pr().openableUrl())
        assertNull(pr(url = "javascript:alert(1)").openableUrl())
        assertNull(pr(url = "/relative").openableUrl())
        assertNull(pr(url = "").openableUrl())
    }

    @Test
    fun codeConversationPortsUpstream() {
        assertTrue(isCodeConversation("attached", 0))
        assertTrue(isCodeConversation(null, 1))
        assertFalse(isCodeConversation("without_attached", 0))
        assertFalse(isCodeConversation(null, 0))
    }

    @Test
    fun conversationVerdictPrefersFieldsThenTheListStampThenTheCache() {
        assertEquals(true, Conversation(codeWorkspaces = listOf(CodeWorkspaceSelection("m", "w"))).isCodeConversation)
        assertEquals(false, Conversation(codeDecisionListed = true).isCodeConversation)
        assertEquals(true, Conversation(codeDecisionListed = true, codeEnvironmentMode = "attached").isCodeConversation)
        assertNull(Conversation().isCodeConversation)
        assertEquals(true, Conversation(cachedIsCodeConversation = true).isCodeConversation)
    }

    @Test
    fun gateNeedsTheFlagAndRc1() {
        val on = StartupConfig(pullRequestsEnabled = true, pullRequestsBatchVersion = 1)
        assertTrue(pullRequestsAvailable(on, "0.8.9-rc1"))
        assertFalse(pullRequestsAvailable(on, "0.8.8"))
        assertFalse(pullRequestsAvailable(on, null))
        assertFalse(pullRequestsAvailable(StartupConfig(), "0.8.9-rc1"))
        assertTrue(pullRequestBatchAvailable(on, "0.8.9-rc1"))
        assertFalse(pullRequestBatchAvailable(on.copy(pullRequestsBatchVersion = 2), "0.8.9-rc1"))
    }

    @Test
    fun wireDecodesNullAndBatchErrors() {
        val json = Json { ignoreUnknownKeys = true }
        assertNull(json.decodeFromString(ConversationPullRequestResponse.serializer(), """{"pullRequest":null}""").pullRequest)
        val batch = json.decodeFromString(
            ConversationPullRequestsResponse.serializer(),
            """{"results":[{"conversationId":"a","pullRequest":{"number":7,"title":"t","url":"u","additions":1,
               "deletions":2,"state":"open","isDraft":false,"mergeable":"clean","checks":"none","future":1}},
               {"conversationId":"b","pullRequest":null},{"conversationId":"c","error":{"code":"RATE_LIMITED"}}]}""",
        )
        assertEquals(7, batch.results[0].pullRequest?.number)
        assertNull(batch.results[1].pullRequest)
        assertEquals(PullRequestErrorCodes.RATE_LIMITED, batch.results[2].error?.code)
    }
}
