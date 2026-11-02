package com.garfiec.librechat.core.model.pullrequest

import com.garfiec.librechat.core.model.config.isSafeLinkUrl

/** What colours a pull request glyph, dot or badge. [MERGED] is the purple GitHub uses. */
enum class PullRequestTone { SUCCESS, ERROR, WARNING, NEUTRAL, MERGED }

enum class PullRequestIcon { OPEN, DRAFT, MERGED, CLOSED }

enum class PullRequestStateLabel { OPEN, DRAFT, MERGED, CLOSED, CONFLICTS }

enum class PullRequestChecksLabel { PASSING, FAILING, RUNNING, NONE }

enum class PullRequestProvider { GITHUB, GITLAB, BITBUCKET }

/**
 * How a pull request looks, everywhere it shows, so the chat item, the sheet and the drawer mark
 * never disagree. Labels are keys the UI resolves to strings.
 */
data class PullRequestPresentation(
    val icon: PullRequestIcon,
    val iconTone: PullRequestTone,
    /** The CI dot on the glyph's corner; null when it shows none (not open, or no checks). */
    val dotTone: PullRequestTone?,
    val state: PullRequestStateLabel,
    val stateTone: PullRequestTone,
    val checks: PullRequestChecksLabel,
    val checksTone: PullRequestTone,
    val provider: PullRequestProvider,
) {
    /** GitLab calls it a merge request, labelled `MR !7`; the others `PR #7`. */
    val isMergeRequest: Boolean get() = provider == PullRequestProvider.GITLAB
}

/**
 * MIRRORED from upstream `presentPullRequest` (`client/src/components/Chat/PullRequest/status.ts`,
 * registered in `scripts/mirrors.json`). A value this client does not know reads as the closest
 * neutral one: an unknown state as open, unknown checks as none, an unknown provider as GitHub.
 */
fun presentPullRequest(pr: ConversationPullRequest): PullRequestPresentation {
    val state = stateOf(pr)
    val checks = checksOf(pr.checks)
    return PullRequestPresentation(
        icon = state.icon,
        iconTone = state.iconTone,
        dotTone = if (pr.state == "open") checks.dot else null,
        state = state.label,
        stateTone = state.tone,
        checks = checks.label,
        checksTone = checks.tone,
        provider = when (pr.provider) {
            "gitlab" -> PullRequestProvider.GITLAB
            "bitbucket" -> PullRequestProvider.BITBUCKET
            else -> PullRequestProvider.GITHUB
        },
    )
}

private class StateView(
    val icon: PullRequestIcon,
    val iconTone: PullRequestTone,
    val label: PullRequestStateLabel,
    val tone: PullRequestTone,
)

private fun stateOf(pr: ConversationPullRequest): StateView = when {
    pr.state == "merged" ->
        StateView(PullRequestIcon.MERGED, PullRequestTone.MERGED, PullRequestStateLabel.MERGED, PullRequestTone.MERGED)
    pr.state == "closed" ->
        StateView(PullRequestIcon.CLOSED, PullRequestTone.NEUTRAL, PullRequestStateLabel.CLOSED, PullRequestTone.NEUTRAL)
    pr.isDraft ->
        StateView(PullRequestIcon.DRAFT, PullRequestTone.NEUTRAL, PullRequestStateLabel.DRAFT, PullRequestTone.NEUTRAL)
    pr.mergeable == "conflicting" ->
        StateView(PullRequestIcon.OPEN, PullRequestTone.ERROR, PullRequestStateLabel.CONFLICTS, PullRequestTone.ERROR)
    else -> StateView(
        PullRequestIcon.OPEN,
        if (pr.mergeable == "clean") PullRequestTone.SUCCESS else PullRequestTone.NEUTRAL,
        PullRequestStateLabel.OPEN,
        PullRequestTone.SUCCESS,
    )
}

private class ChecksView(val label: PullRequestChecksLabel, val tone: PullRequestTone, val dot: PullRequestTone?)

private fun checksOf(checks: String): ChecksView = when (checks) {
    "passing" -> ChecksView(PullRequestChecksLabel.PASSING, PullRequestTone.SUCCESS, PullRequestTone.SUCCESS)
    "failing" -> ChecksView(PullRequestChecksLabel.FAILING, PullRequestTone.ERROR, PullRequestTone.ERROR)
    "running" -> ChecksView(PullRequestChecksLabel.RUNNING, PullRequestTone.WARNING, PullRequestTone.WARNING)
    else -> ChecksView(PullRequestChecksLabel.NONE, PullRequestTone.NEUTRAL, null)
}

/** Whether the pull request's link may be opened: an absolute http(s) URL that [isSafeLinkUrl] passes. */
fun ConversationPullRequest.openableUrl(): String? {
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return null
    return trimmed.takeIf(::isSafeLinkUrl)
}
