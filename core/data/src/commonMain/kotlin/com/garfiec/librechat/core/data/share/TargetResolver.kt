package com.garfiec.librechat.core.data.share

/**
 * Names the message a link from the open chat shares up to: the tail of the branch on screen, as
 * the web client's share dialog does (scripts/mirrors.json `share-target-branch-tail`). Supplied
 * by the chat screen only; the drawer, lists and settings share without a target, which shares
 * the conversation's newest branch.
 *
 * Called before each Create and Update, and once more after the server rejects the target
 * (`TARGET_MESSAGE_NOT_FOUND` / `NO_MESSAGES`), so it must read the branch afresh every time.
 */
fun interface TargetResolver {
    suspend fun resolve(): ShareTarget
}

sealed interface ShareTarget {
    /** The branch tail, confirmed saved on the server. */
    data class Saved(val messageId: String) : ShareTarget

    /** The branch tail exists on screen but the server doesn't have it yet (still streaming or saving). */
    data object NotSaved : ShareTarget

    data object NoMessages : ShareTarget
}
