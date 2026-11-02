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
    val (checks, checksTone, dot) = when (pr.checks) {
        "passing" -> Triple(PullRequestChecksLabel.PASSING, PullRequestTone.SUCCESS, PullRequestTone.SUCCESS)
        "failing" -> Triple(PullRequestChecksLabel.FAILING, PullRequestTone.ERROR, PullRequestTone.ERROR)
        "running" -> Triple(PullRequestChecksLabel.RUNNING, PullRequestTone.WARNING, PullRequestTone.WARNING)
        else -> Triple(PullRequestChecksLabel.NONE, PullRequestTone.NEUTRAL, null)
    }
    val provider = when (pr.provider) {
        "gitlab" -> PullRequestProvider.GITLAB
        "bitbucket" -> PullRequestProvider.BITBUCKET
        else -> PullRequestProvider.GITHUB
    }
    return PullRequestPresentation(
        icon = when {
            pr.state == "merged" -> PullRequestIcon.MERGED
            pr.state == "closed" -> PullRequestIcon.CLOSED
            pr.isDraft -> PullRequestIcon.DRAFT
            else -> PullRequestIcon.OPEN
        },
        iconTone = when {
            pr.state == "merged" -> PullRequestTone.MERGED
            pr.state == "closed" || pr.isDraft -> PullRequestTone.NEUTRAL
            pr.mergeable == "conflicting" -> PullRequestTone.ERROR
            pr.mergeable == "clean" -> PullRequestTone.SUCCESS
            else -> PullRequestTone.NEUTRAL
        },
        dotTone = if (pr.state == "open") dot else null,
        state = when {
            pr.state == "merged" -> PullRequestStateLabel.MERGED
            pr.state == "closed" -> PullRequestStateLabel.CLOSED
            pr.isDraft -> PullRequestStateLabel.DRAFT
            pr.mergeable == "conflicting" -> PullRequestStateLabel.CONFLICTS
            else -> PullRequestStateLabel.OPEN
        },
        stateTone = when {
            pr.state == "merged" -> PullRequestTone.MERGED
            pr.state == "closed" || pr.isDraft -> PullRequestTone.NEUTRAL
            pr.mergeable == "conflicting" -> PullRequestTone.ERROR
            else -> PullRequestTone.SUCCESS
        },
        checks = checks,
        checksTone = checksTone,
        provider = provider,
    )
}

/** Whether the pull request's link may be opened: an absolute http(s) URL that [isSafeLinkUrl] passes. */
fun ConversationPullRequest.openableUrl(): String? {
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return null
    return trimmed.takeIf(::isSafeLinkUrl)
}
