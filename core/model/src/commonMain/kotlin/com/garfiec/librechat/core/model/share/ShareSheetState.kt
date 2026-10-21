package com.garfiec.librechat.core.model.share

/**
 * What the share sheet shows for one conversation. The `:core:data` controller produces it from
 * the link lookup, `/api/config` and the user's role; the `:core:ui` sheet renders it and decides
 * nothing beyond which controls a state shows.
 */
sealed interface ShareSheetState {

    data object Closed : ShareSheetState

    /** Opening the sheet never creates a link. */
    data object Loading : ShareSheetState

    /**
     * The conversation has no live link.
     *
     * @param canCreate the server allows shared links and the user's role may create them; false
     *   keeps Create disabled with the reason shown.
     * @param filesOption null when the server has file snapshots off, which hides the switch.
     */
    data class NoLink(
        val canCreate: Boolean,
        val filesOption: FilesOption?,
        val audience: ShareAudience,
        val busy: ShareSheetBusy? = null,
        val error: ShareSheetError? = null,
    ) : ShareSheetState

    /**
     * The conversation has a live link at [url].
     *
     * @param canUpdate the user may republish it (role permission); false hides Update and the
     *   files switch, which only takes effect on Update.
     * @param updateKeepsUrl the server republishes in place; false means Update issues a new URL,
     *   which the confirmation says.
     * @param filesOption the link's stored file choice, or null when the server has file
     *   snapshots off. Toggling it changes only this value; nothing is sent until Update.
     * @param audience who a public grant on this link reaches ([ShareAudience.ANYONE] or
     *   [ShareAudience.SIGNED_IN]), which labels the Manage access switch. Whether the link has
     *   that grant is the switch's state, since Manage access can change it after creation.
     * @param reduced the sheet was opened on a link that is not the conversation's newest (an
     *   older or targeted link made on the web). The list carries neither its `_id` nor its stored
     *   file choice, so only URL, Copy, Share…, QR, Preview and Delete are offered.
     */
    data class Linked(
        val url: String,
        val shareId: String,
        val canUpdate: Boolean,
        val updateKeepsUrl: Boolean,
        val filesOption: FilesOption?,
        val audience: ShareAudience,
        val canManageAccess: Boolean = false,
        val canPreview: Boolean = false,
        val reduced: Boolean = false,
        val busy: ShareSheetBusy? = null,
        val error: ShareSheetError? = null,
    ) : ShareSheetState

    data class Failed(val error: ShareSheetError) : ShareSheetState
}

/** The "Include files" switch, present only when the server snapshots files into shared links. */
data class FilesOption(val includeFiles: Boolean) {
    companion object {
        /** A new link includes files, as the web client's switch starts on. */
        val NEW_LINK = FilesOption(includeFiles = true)
    }
}

/**
 * Who can open a link, as this server's policy, not a per-link setting: the list carries no
 * reliable per-link visibility. A new link gets the public viewer grant only when the user's role
 * has SHARED_LINKS.SHARE_PUBLIC; that grant then reaches anyone, or only signed-in people, by
 * `publicSharedLinksEnabled`.
 */
enum class ShareAudience {
    /** The public grant reaches anyone with the link, signed in or not. */
    ANYONE,

    /** The public grant reaches only people signed in to the server. */
    SIGNED_IN,

    /**
     * No public grant: only the owner and the people and groups they add can open the link. Only
     * a new link is described this way; [ShareSheetState.Linked.audience] is who a public grant
     * would reach, for the Manage access switch.
     */
    PEOPLE_ADDED,
}

/** The action in flight; the sheet holds every action until it lands. */
enum class ShareSheetBusy { CREATING, UPDATING, DELETING }

/** Why an action or the lookup failed, as the sheet words it. */
sealed interface ShareSheetError {
    data object Network : ShareSheetError
    data object Forbidden : ShareSheetError
    data object NotFound : ShareSheetError
    data object RateLimited : ShareSheetError

    /** The branch tail is not saved on the server yet, even after one re-resolved retry. */
    data object TargetNotSaved : ShareSheetError

    /** The chat has no saved messages to share. */
    data object NoMessages : ShareSheetError

    /** The server's content filter refused the share; [serverMessage] is its explanation, if any. */
    data class ContentBlocked(val serverMessage: String?) : ShareSheetError
    data object Generic : ShareSheetError
}
