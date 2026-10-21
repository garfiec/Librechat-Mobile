package com.garfiec.librechat.core.ui.components.share

import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState

/**
 * Which controls the share sheet shows for a [ShareSheetState]. The sheet reads only this, so the
 * rules live in one pure place and are tested here rather than through the composable.
 */
internal data class ShareSheetControls(
    val progress: Boolean = false,
    /** Who a new link reaches: anyone, signed-in people, or only the owner and people they add. */
    val audience: ShareAudience? = null,
    val create: Boolean = false,
    val createEnabled: Boolean = false,
    val cannotCreateReason: Boolean = false,
    /** The URL with Copy, Share… and the QR code. */
    val url: String? = null,
    val snapshotNote: Boolean = false,
    val reducedNote: Boolean = false,
    /** The Include files switch and its value; null hides it. */
    val files: FilesOption? = null,
    /** The switch's caption says it takes effect on Update (a live link). */
    val filesAppliesOnUpdate: Boolean = false,
    val update: Boolean = false,
    val preview: Boolean = false,
    val manageAccess: Boolean = false,
    val delete: Boolean = false,
    val retry: Boolean = false,
    /**
     * The action in flight; it shows a spinner and every action is held until it lands, Copy,
     * Preview and Manage access included: each acts on a link that is being replaced or revoked.
     */
    val busy: ShareSheetBusy? = null,
    val error: ShareSheetError? = null,
) {
    val enabled: Boolean get() = busy == null

    companion object {
        fun of(state: ShareSheetState): ShareSheetControls = when (state) {
            ShareSheetState.Closed -> ShareSheetControls()
            ShareSheetState.Loading -> ShareSheetControls(progress = true)
            is ShareSheetState.Failed -> ShareSheetControls(retry = true, error = state.error)
            is ShareSheetState.NoLink -> ShareSheetControls(
                audience = state.audience,
                create = true,
                createEnabled = state.canCreate && state.busy == null,
                cannotCreateReason = !state.canCreate,
                // The choice only matters for a link the user can create.
                files = state.filesOption.takeIf { state.canCreate },
                busy = state.busy,
                error = state.error,
            )
            is ShareSheetState.Linked -> linked(state)
        }

        private fun linked(state: ShareSheetState.Linked): ShareSheetControls {
            // A non-newest link: the list gives neither its _id nor its stored file choice, so
            // Update, the switch and Manage access are off whatever the gates say.
            val canUpdate = state.canUpdate && !state.reduced
            return ShareSheetControls(
                url = state.url,
                snapshotNote = !state.reduced,
                reducedNote = state.reduced,
                // The switch applies only on Update, so without Update it would do nothing.
                files = state.filesOption.takeIf { canUpdate },
                filesAppliesOnUpdate = true,
                update = canUpdate,
                preview = state.canPreview,
                manageAccess = state.canManageAccess && !state.reduced,
                delete = true,
                busy = state.busy,
                error = state.error,
            )
        }
    }
}
