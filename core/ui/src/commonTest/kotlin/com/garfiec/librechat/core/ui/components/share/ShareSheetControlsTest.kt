package com.garfiec.librechat.core.ui.components.share

import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareSheetControlsTest {

    private val linked = ShareSheetState.Linked(
        url = "https://chat.example.com/share/abc",
        shareId = "abc",
        canUpdate = true,
        updateKeepsUrl = true,
        filesOption = FilesOption(includeFiles = false),
        audience = ShareAudience.SIGNED_IN,
    )

    private val noLink = ShareSheetState.NoLink(
        canCreate = true,
        filesOption = FilesOption.NEW_LINK,
        audience = ShareAudience.ANYONE,
    )

    @Test
    fun closedShowsNothing() {
        assertEquals(ShareSheetControls(), ShareSheetControls.of(ShareSheetState.Closed))
    }

    @Test
    fun noLinkShowsAnOwnerOnlyAudienceAsIs() {
        val controls = ShareSheetControls.of(noLink.copy(audience = ShareAudience.PEOPLE_ADDED))
        assertEquals(ShareAudience.PEOPLE_ADDED, controls.audience)
    }

    @Test
    fun loadingShowsOnlyProgress() {
        val controls = ShareSheetControls.of(ShareSheetState.Loading)
        assertEquals(ShareSheetControls(progress = true), controls)
    }

    @Test
    fun failedShowsRetryWithTheError() {
        val controls = ShareSheetControls.of(ShareSheetState.Failed(ShareSheetError.Network))
        assertTrue(controls.retry)
        assertEquals(ShareSheetError.Network, controls.error)
        assertFalse(controls.create)
        assertNull(controls.url)
    }

    @Test
    fun noLinkOffersCreateWithAudienceAndFilesOnByDefault() {
        val controls = ShareSheetControls.of(noLink)
        assertTrue(controls.create)
        assertTrue(controls.createEnabled)
        assertFalse(controls.cannotCreateReason)
        assertEquals(ShareAudience.ANYONE, controls.audience)
        assertEquals(FilesOption(includeFiles = true), controls.files)
        assertNull(controls.url)
        assertFalse(controls.update)
        assertFalse(controls.delete)
    }

    @Test
    fun noLinkWithoutCreatePermissionDisablesCreateAndSaysWhy() {
        val controls = ShareSheetControls.of(noLink.copy(canCreate = false))
        assertTrue(controls.create)
        assertFalse(controls.createEnabled)
        assertTrue(controls.cannotCreateReason)
        assertNull(controls.files)
    }

    @Test
    fun serverWithoutFileSnapshotsHidesTheSwitch() {
        assertNull(ShareSheetControls.of(noLink.copy(filesOption = null)).files)
        assertNull(ShareSheetControls.of(linked.copy(filesOption = null)).files)
    }

    @Test
    fun creatingHoldsCreateAndMarksItBusy() {
        val controls = ShareSheetControls.of(noLink.copy(busy = ShareSheetBusy.CREATING))
        assertFalse(controls.createEnabled)
        assertFalse(controls.enabled)
        assertEquals(ShareSheetBusy.CREATING, controls.busy)
    }

    @Test
    fun linkedShowsTheFullSet() {
        val controls = ShareSheetControls.of(linked)
        assertEquals(linked.url, controls.url)
        assertTrue(controls.snapshotNote)
        assertFalse(controls.reducedNote)
        assertEquals(FilesOption(includeFiles = false), controls.files)
        assertTrue(controls.filesAppliesOnUpdate)
        assertTrue(controls.update)
        assertTrue(controls.delete)
        assertTrue(controls.enabled)
        assertFalse(controls.create)
        assertNull(controls.audience)
    }

    @Test
    fun previewAndManageAccessStayHiddenUntilTheStateAllowsThem() {
        val hidden = ShareSheetControls.of(linked)
        assertFalse(hidden.preview)
        assertFalse(hidden.manageAccess)

        val shown = ShareSheetControls.of(linked.copy(canPreview = true, canManageAccess = true))
        assertTrue(shown.preview)
        assertTrue(shown.manageAccess)
    }

    @Test
    fun withoutUpdatePermissionTheSwitchGoesWithUpdate() {
        val controls = ShareSheetControls.of(linked.copy(canUpdate = false))
        assertFalse(controls.update)
        assertNull(controls.files)
        assertTrue(controls.delete)
        assertEquals(linked.url, controls.url)
    }

    @Test
    fun reducedLinkKeepsOnlyUrlPreviewAndDelete() {
        val controls = ShareSheetControls.of(
            linked.copy(reduced = true, canPreview = true, canManageAccess = true),
        )
        assertEquals(linked.url, controls.url)
        assertTrue(controls.reducedNote)
        assertFalse(controls.snapshotNote)
        assertFalse(controls.update)
        assertNull(controls.files)
        assertFalse(controls.manageAccess)
        assertTrue(controls.preview)
        assertTrue(controls.delete)
    }

    @Test
    fun busyLinkHoldsEveryActionAndCarriesTheError() {
        val controls = ShareSheetControls.of(
            linked.copy(busy = ShareSheetBusy.UPDATING, error = ShareSheetError.ContentBlocked("blocked")),
        )
        assertFalse(controls.enabled)
        assertEquals(ShareSheetBusy.UPDATING, controls.busy)
        assertEquals(ShareSheetError.ContentBlocked("blocked"), controls.error)
    }

    @Test
    fun busyLinkKeepsPreviewAndManageAccessInPlaceButHeld() {
        val idle = linked.copy(canPreview = true, canManageAccess = true)
        for (busy in listOf(ShareSheetBusy.UPDATING, ShareSheetBusy.DELETING)) {
            val controls = ShareSheetControls.of(idle.copy(busy = busy))
            // The rows stay so the sheet doesn't jump, but the sheet draws them with enabled = false:
            // Preview would close it and drop the result of a change that is still running.
            assertTrue(controls.preview, "preview shown while $busy")
            assertTrue(controls.manageAccess, "manage access shown while $busy")
            assertEquals(linked.url, controls.url)
            assertFalse(controls.enabled, "actions held while $busy")
        }
    }
}
