package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** Who a link reaches: the new-link audience note and the Manage access row. */
class ShareLinkControllerAccessTest : ShareLinkControllerTestBase() {

    // ── who a new link reaches ─────────────────────────────────────────────

    @Test
    fun `without SHARE_PUBLIC a new link is only for the owner and people they add`() = runTest {
        allowSharePublic(false)
        coEvery { shareApi.getShareLink("convo") } returns live("s1") andThen none
        coEvery { shareApi.deleteShareLink("s1") } just runs
        val controller = controller()

        controller.open("convo")
        // A live link's audience labels the Manage access switch: who a public grant reaches.
        assertThat(controller.awaitLinked().audience).isEqualTo(ShareAudience.ANYONE)
        controller.delete()

        // The env flag says anyone, but the create route grants no public access to this role.
        assertThat(controller.awaitNoLink().audience).isEqualTo(ShareAudience.PEOPLE_ADDED)
    }

    @Test
    fun `an unknown role fails closed on who a new link reaches`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo")

        assertThat(controller.awaitNoLink().audience).isEqualTo(ShareAudience.PEOPLE_ADDED)
    }

    @Test
    fun `a server older than link ACLs keeps the flag-only wording`() = runTest {
        version.value = "0.8.7"
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo")

        assertThat(controller.awaitNoLink().audience).isEqualTo(ShareAudience.ANYONE)
    }

    // ── manage access ──────────────────────────────────────────────────────

    private fun allowShare(share: Boolean = true) {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to true, "SHARE" to share)),
        )
    }

    private fun grantShareBit(resourceId: String) {
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", resourceId) } returns
            Result.Success(PermissionBits.VIEW or PermissionBits.SHARE)
    }

    @Test
    fun `manage access is offered with the role's SHARE and the link's SHARE bit`() = runTest {
        allowShare()
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")

        controller.await { it is ShareSheetState.Linked && it.canManageAccess }
        coVerify { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") }
    }

    @Test
    fun `manage access stays hidden without the link's SHARE bit`() = runTest {
        allowShare()
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") } returns
            Result.Success(PermissionBits.VIEW)
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `manage access fails closed on an unknown role and never asks for the bit`() = runTest {
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
        coVerify(exactly = 0) { permissionsRepository.getEffectivePermissions(any(), any()) }
    }

    @Test
    fun `manage access stays hidden when the role denies SHARE`() = runTest {
        allowShare(share = false)
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `manage access stays hidden when the bit can't be read`() = runTest {
        allowShare()
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") } returns
            Result.Error(IOException("offline"))
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `a reduced link never offers manage access`() = runTest {
        allowShare()
        grantShareBit("acl-newest")
        coEvery { shareApi.getShareLink("convo") } returns live("newest")
        val controller = controller()

        controller.open("convo", focusShareId = "older")
        controller.awaitLinked()
        settle()

        val state = controller.state.value as ShareSheetState.Linked
        assertThat(state.reduced).isTrue()
        assertThat(state.canManageAccess).isFalse()
        coVerify(exactly = 0) { permissionsRepository.getEffectivePermissions(any(), any()) }
    }

    @Test
    fun `openAccess reads the link's grants by its lookup id, and closing the sheet closes it`() = runTest {
        allowShare()
        grantShareBit("acl-s1")
        coEvery { permissionsRepository.getResourcePermissions("sharedLink", "acl-s1") } returns
            Result.Success(ResourcePermissions())
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()
        controller.open("convo")
        controller.await { it is ShareSheetState.Linked && it.canManageAccess }

        controller.openAccess()
        controller.access.state.first { it is AclState.Ready }
        controller.close()

        assertThat(controller.access.state.first { it == AclState.Closed }).isEqualTo(AclState.Closed)
        coVerify(exactly = 1) { permissionsRepository.getResourcePermissions("sharedLink", "acl-s1") }
    }

    @Test
    fun `openAccess does nothing while manage access isn't offered`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()
        controller.open("convo")
        controller.awaitLinked()
        settle()

        controller.openAccess()
        settle()

        assertThat(controller.access.state.value).isEqualTo(AclState.Closed)
        coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }
    }
}
