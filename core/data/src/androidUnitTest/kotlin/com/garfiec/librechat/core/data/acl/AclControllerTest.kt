package com.garfiec.librechat.core.data.acl

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepositoryImpl
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.AccessRoleIds
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.acl.AclError
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.network.api.PermissionsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * The shared-link access editor's controller, over the real repository and a mocked API, so error
 * bodies travel the same path they do in the app (HTTP exception → safeApiCall → editor error).
 */
class AclControllerTest {

    private val api = mockk<PermissionsApi>()
    private val role = MutableStateFlow<UserRolePermissions?>(fullRole())
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns role
    }
    private val repository = PermissionsRepositoryImpl(api)

    private val owner = Principal(
        type = PrincipalType.USER,
        id = "me",
        name = "Me",
        accessRoleId = AccessRoleIds.SHARED_LINK_OWNER,
    )
    private val viewer = Principal(
        type = PrincipalType.USER,
        id = "u2",
        name = "Bea",
        accessRoleId = AccessRoleIds.SHARED_LINK_VIEWER,
    )
    private val group = Principal(type = PrincipalType.GROUP, id = "g1", name = "Design")

    private val roles = listOf(
        AccessRole(accessRoleId = AccessRoleIds.SHARED_LINK_VIEWER, name = "com_ui_role_viewer"),
        AccessRole(accessRoleId = AccessRoleIds.SHARED_LINK_OWNER, name = "com_ui_role_owner"),
    )

    private fun fullRole(
        sharePublic: Boolean = true,
        viewUsers: Boolean = true,
        viewGroups: Boolean = true,
    ) = UserRolePermissions(
        name = "USER",
        permissions = mapOf(
            "SHARED_LINKS" to mapOf("CREATE" to true, "SHARE" to true, "SHARE_PUBLIC" to sharePublic),
            "PEOPLE_PICKER" to mapOf("VIEW_USERS" to viewUsers, "VIEW_GROUPS" to viewGroups),
        ),
    )

    private fun TestScope.controller() = AclController(AclPolicy.SharedLink, repository, roleRepository, backgroundScope)

    private suspend fun AclController.awaitReady(): AclState.Ready =
        state.first { it is AclState.Ready && !it.busy } as AclState.Ready

    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun grants(vararg principals: Principal, public: Boolean = false) = ResourcePermissions(
        principals = principals.toList(),
        public = public,
        publicAccessRoleId = if (public) AccessRoleIds.SHARED_LINK_VIEWER else null,
    )

    private fun serve(vararg principals: Principal, public: Boolean = false) {
        coEvery { api.getResourceRoles("sharedLink") } returns roles
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(*principals, public = public)
    }

    private fun captureUpdates(): MutableList<UpdateResourcePermissionsRequest> {
        val sent = mutableListOf<UpdateResourcePermissionsRequest>()
        val body = slot<UpdateResourcePermissionsRequest>()
        coEvery { api.updateResourcePermissions("sharedLink", "link1", capture(body)) } answers {
            sent += body.captured
            ResourcePermissions()
        }
        return sent
    }

    // ── load ───────────────────────────────────────────────────────────────

    @Test
    fun `the owner is listed first and locked, and the owner role is never offered`() = runTest {
        serve(viewer, owner)
        val controller = controller()

        controller.open("link1")
        val state = controller.awaitReady()

        assertThat(state.entries.map { it.principal.id }).containsExactly("me", "u2").inOrder()
        assertThat(state.entries.first().locked).isTrue()
        assertThat(state.entries.last().locked).isFalse()
        assertThat(state.roles.map { it.accessRoleId }).containsExactly(AccessRoleIds.SHARED_LINK_VIEWER)
        assertThat(state.grantRoleId).isEqualTo(AccessRoleIds.SHARED_LINK_VIEWER)
    }

    @Test
    fun `new grants default to the viewer role even when the roles can't be read`() = runTest {
        coEvery { api.getResourceRoles("sharedLink") } throws IOException("offline")
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner)
        val controller = controller()

        controller.open("link1")

        assertThat(controller.awaitReady().grantRoleId).isEqualTo(AccessRoleIds.SHARED_LINK_VIEWER)
    }

    @Test
    fun `a failed read offers retry, which reads again`() = runTest {
        coEvery { api.getResourceRoles("sharedLink") } returns roles
        coEvery { api.getResourcePermissions("sharedLink", "link1") } throws
            ApiException(403, "Forbidden") andThen grants(owner)
        val controller = controller()

        controller.open("link1")
        val failed = controller.state.first { it is AclState.Failed } as AclState.Failed
        assertThat(failed.error).isEqualTo(AclError.FORBIDDEN)

        controller.retry()
        assertThat(controller.awaitReady().entries).hasSize(1)
    }

    // ── gates ──────────────────────────────────────────────────────────────

    @Test
    fun `public access and people search fail closed on an unknown role`() = runTest {
        role.value = null
        serve(owner)
        val controller = controller()

        controller.open("link1")
        val state = controller.awaitReady()

        assertThat(state.canSharePublic).isFalse()
        assertThat(state.canSearch).isFalse()
    }

    @Test
    fun `without SHARE_PUBLIC the public switch is hidden and setPublic sends nothing`() = runTest {
        role.value = fullRole(sharePublic = false)
        serve(owner)
        val sent = captureUpdates()
        val controller = controller()
        controller.open("link1")
        assertThat(controller.awaitReady().canSharePublic).isFalse()

        controller.setPublic(true)
        settle()

        assertThat(sent).isEmpty()
    }

    @Test
    fun `either people picker permission allows search`() = runTest {
        role.value = fullRole(viewUsers = false, viewGroups = true)
        serve(owner)
        val controller = controller()

        controller.open("link1")

        assertThat(controller.awaitReady().canSearch).isTrue()
    }

    @Test
    fun `without the people picker no search is sent`() = runTest {
        role.value = fullRole(viewUsers = false, viewGroups = false)
        serve(owner)
        val controller = controller()
        controller.open("link1")
        assertThat(controller.awaitReady().canSearch).isFalse()

        controller.onSearchQueryChange("bea")
        advanceTimeBy(1_000)
        settle()

        coVerify(exactly = 0) { api.searchPrincipals(any(), any()) }
    }

    // ── writes ─────────────────────────────────────────────────────────────

    @Test
    fun `granting sends the person with the viewer role, leaves public alone and reads the grants again`() = runTest {
        serve(owner, public = true)
        val sent = captureUpdates()
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner, viewer, public = true)

        controller.grant(viewer.copy(accessRoleId = null))
        val state = controller.state.first { it is AclState.Ready && it.entries.size == 2 } as AclState.Ready

        assertThat(sent).hasSize(1)
        assertThat(sent[0].public).isNull()
        assertThat(sent[0].updated.single().accessRoleId).isEqualTo(AccessRoleIds.SHARED_LINK_VIEWER)
        assertThat(state.isPublic).isTrue()
        coVerify(exactly = 2) { api.getResourcePermissions("sharedLink", "link1") }
    }

    @Test
    fun `revoking sends the person in removed and leaves public alone`() = runTest {
        serve(owner, viewer)
        val sent = captureUpdates()
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner)

        controller.revoke(viewer)
        controller.state.first { it is AclState.Ready && it.entries.size == 1 }

        assertThat(sent.single().removed.single().id).isEqualTo("u2")
        assertThat(sent.single().public).isNull()
    }

    @Test
    fun `the owner can't be revoked`() = runTest {
        serve(owner, viewer)
        val sent = captureUpdates()
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.revoke(owner)
        settle()

        assertThat(sent).isEmpty()
    }

    @Test
    fun `turning public on sends the viewer role, and off sends an explicit false`() = runTest {
        serve(owner)
        val sent = captureUpdates()
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner, public = true)
        controller.setPublic(true)
        controller.state.first { it is AclState.Ready && it.isPublic && !it.busy }
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner)
        controller.setPublic(false)
        controller.state.first { it is AclState.Ready && !it.isPublic && !it.busy }

        assertThat(sent.map { it.public }).containsExactly(true, false).inOrder()
        assertThat(sent[0].publicAccessRoleId).isEqualTo(AccessRoleIds.SHARED_LINK_VIEWER)
        assertThat(sent[1].publicAccessRoleId).isNull()
    }

    @Test
    fun `a change that landed shows even when reading the grants back fails`() = runTest {
        serve(owner)
        captureUpdates()
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()
        coEvery { api.getResourcePermissions("sharedLink", "link1") } throws IOException("offline")

        controller.grant(group)
        val state = controller.state.first { it is AclState.Ready && it.entries.size == 2 } as AclState.Ready

        assertThat(state.error).isNull()
        assertThat(state.entries.last().principal.accessRoleId).isEqualTo(AccessRoleIds.SHARED_LINK_VIEWER)
    }

    @Test
    fun `the server's owner protection reads as its own error`() = runTest {
        serve(owner)
        coEvery { api.updateResourcePermissions("sharedLink", "link1", any()) } throws ApiException(
            statusCode = 400,
            message = "Bad Request",
            body = """{"error":"Bad Request","message":"Shared link owner permissions cannot be changed"}""",
            serverAuthored = true,
        )
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.grant(group)
        val state = controller.state.first { it is AclState.Ready && it.error != null } as AclState.Ready

        assertThat(state.error).isEqualTo(AclError.OWNER_PROTECTED)
        assertThat(state.busy).isFalse()
    }

    @Test
    fun `any other 400 is a generic error`() = runTest {
        serve(owner)
        coEvery { api.updateResourcePermissions("sharedLink", "link1", any()) } throws ApiException(
            statusCode = 400,
            message = "Failed to update permissions",
            body = """{"error":"Failed to update permissions","details":"boom"}""",
        )
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.grant(group)
        val state = controller.state.first { it is AclState.Ready && it.error != null } as AclState.Ready

        assertThat(state.error).isEqualTo(AclError.GENERIC)
    }

    // ── search ─────────────────────────────────────────────────────────────

    @Test
    fun `search results leave out whoever already has access`() = runTest {
        serve(owner, viewer)
        coEvery { api.searchPrincipals("be", any()) } returns listOf(viewer.copy(accessRoleId = null), group)
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.onSearchQueryChange("be")
        val state = controller.state.first { it is AclState.Ready && it.search.results.isNotEmpty() } as AclState.Ready

        assertThat(state.search.results.map { it.id }).containsExactly("g1")
    }

    @Test
    fun `a one-character query is not sent, since the server refuses it`() = runTest {
        serve(owner)
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.onSearchQueryChange(" b ")
        advanceTimeBy(1_000)
        settle()
        val state = controller.state.first { it is AclState.Ready && it.search.query == " b " } as AclState.Ready

        coVerify(exactly = 0) { api.searchPrincipals(any(), any()) }
        assertThat(state.search.failed).isFalse()
        assertThat(state.search.searching).isFalse()
    }

    @Test
    fun `a failed search says so instead of showing no matches`() = runTest {
        serve(owner)
        coEvery { api.searchPrincipals("be", any()) } throws ApiException(403, "Forbidden")
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()

        controller.onSearchQueryChange("be")
        val state = controller.state.first { it is AclState.Ready && it.search.failed } as AclState.Ready

        assertThat(state.search.results).isEmpty()
        assertThat(state.search.searching).isFalse()
    }

    @Test
    fun `granting clears the search`() = runTest {
        serve(owner)
        captureUpdates()
        coEvery { api.searchPrincipals("de", any()) } returns listOf(group)
        val controller = controller()
        controller.open("link1")
        controller.awaitReady()
        controller.onSearchQueryChange("de")
        controller.state.first { it is AclState.Ready && it.search.results.isNotEmpty() }
        coEvery { api.getResourcePermissions("sharedLink", "link1") } returns grants(owner, group)

        controller.grant(group)
        val state = controller.state.first { it is AclState.Ready && it.entries.size == 2 } as AclState.Ready

        assertThat(state.search.query).isEmpty()
    }

    // ── stale results ──────────────────────────────────────────────────────

    // Over a mocked repository: nothing leaves the test dispatcher, so "dropped" is not "not yet".
    private fun mockedRepository(): PermissionsRepository = mockk {
        coEvery { getResourceRoles("sharedLink") } returns Result.Success(roles)
    }

    @Test
    fun `a read that lands after close is dropped`() = runTest {
        val slow = CompletableDeferred<Result<ResourcePermissions>>()
        val permissions = mockedRepository()
        coEvery { permissions.getResourcePermissions("sharedLink", "link1") } coAnswers { slow.await() }
        val controller = AclController(AclPolicy.SharedLink, permissions, roleRepository, backgroundScope)

        controller.open("link1")
        settle()
        controller.close()
        slow.complete(Result.Success(grants(owner)))
        settle()

        assertThat(controller.state.value).isEqualTo(AclState.Closed)
    }

    @Test
    fun `a write that lands after another open is dropped`() = runTest {
        val slow = CompletableDeferred<Result<ResourcePermissions>>()
        val permissions = mockedRepository()
        coEvery { permissions.getResourcePermissions("sharedLink", any()) } returns Result.Success(grants(owner))
        coEvery { permissions.updateResourcePermissions("sharedLink", "link1", any()) } coAnswers { slow.await() }
        val controller = AclController(AclPolicy.SharedLink, permissions, roleRepository, backgroundScope)
        controller.open("link1")
        settle()
        controller.grant(group)
        settle()
        assertThat((controller.state.value as AclState.Ready).busy).isTrue()

        controller.open("link2")
        settle()
        slow.complete(Result.Success(ResourcePermissions()))
        settle()

        val state = controller.state.value as AclState.Ready
        assertThat(state.busy).isFalse()
        assertThat(state.entries).hasSize(1)
        // link1's open read only: the dropped write never read its grants back.
        coVerify(exactly = 1) { permissions.getResourcePermissions("sharedLink", "link1") }
    }
}
