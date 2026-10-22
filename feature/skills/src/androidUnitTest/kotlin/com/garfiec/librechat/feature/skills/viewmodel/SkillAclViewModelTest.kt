package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillAclViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val permissionsRepository = mockk<PermissionsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = SkillAclViewModel(permissionsRepository, roleRepository)

    private fun rolePerms(vararg granted: String) = UserRolePermissions(
        name = "USER",
        permissions = mapOf("SKILLS" to granted.associateWith { true }),
    )

    /** SKILLS.SHARE plus the people picker, as an admin who opened both to USER would set it. */
    private val sharerWithPicker = UserRolePermissions(
        name = "USER",
        permissions = mapOf(
            "SKILLS" to mapOf("USE" to true, "CREATE" to true, "SHARE" to true, "SHARE_PUBLIC" to false),
            "PEOPLE_PICKER" to mapOf("VIEW_USERS" to true, "VIEW_GROUPS" to true, "VIEW_ROLES" to false),
        ),
    )

    // --- Fail-closed share gates ---

    @Test
    fun `canShare and canSharePublic are false for null role (fail-closed)`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canShare).isFalse()
        assertThat(viewModel.uiState.value.canSharePublic).isFalse()
    }

    @Test
    fun `canShare follows the detail page's resolved access, not the role alone`() =
        runTest(testDispatcher) {
            every { roleRepository.userPermissions } returns MutableStateFlow(rolePerms("SHARE"))
            val viewModel = vm()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.canShare).isFalse()
            viewModel.setCanShare("sk-1", true)
            assertThat(viewModel.uiState.value.canShare).isTrue()
            // SHARE granted but not SHARE_PUBLIC → public toggle stays gated.
            assertThat(viewModel.uiState.value.canSharePublic).isFalse()
        }

    /** The gate can open after the screen's first resume; the grants must still load. */
    @Test
    fun `grants load when the share gate opens, once`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions())
        val viewModel = vm()
        viewModel.setCanShare("sk-1", false)
        advanceUntilIdle()
        coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }

        viewModel.setCanShare("sk-1", true)
        advanceUntilIdle()
        viewModel.setCanShare("sk-1", true)
        advanceUntilIdle()
        coVerify(exactly = 1) { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") }
    }

    @Test
    fun `canSharePublic true with SHARE_PUBLIC granted`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(rolePerms("SHARE", "SHARE_PUBLIC"))
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canSharePublic).isTrue()
    }

    // --- grant / revoke / setPublic apply flow ---

    // The PUT's real body decodes to an empty ResourcePermissions (PermissionsApiUpdateTest), so
    // every stub below answers the PUT with exactly that and the grants come from the GET after it.

    @Test
    fun `grant sends the principal with its access role and reads the grants back`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(emptyList())
        val updatedPerms = ResourcePermissions(
            principals = listOf(Principal(type = "user", id = "u1", accessRoleId = "role_editor")),
        )
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returnsMany
            listOf(Result.Success(ResourcePermissions()), Result.Success(updatedPerms))
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())

        val viewModel = vm()
        viewModel.load("sk-1")
        advanceUntilIdle()
        viewModel.grant(Principal(type = "user", id = "u1"), "role_editor")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.principals.single().accessRoleId).isEqualTo("role_editor")
        // A grant from the search clears it, so the next person starts fresh.
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
        coVerify { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) }
    }

    @Test
    fun `grant and revoke leave public access out of the body`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(emptyList())
        // A public skill: a grant or revoke that sent `public` would risk turning it off.
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer"))
        val bodies = mutableListOf<UpdateResourcePermissionsRequest>()
        coEvery {
            permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(bodies))
        } returns Result.Success(ResourcePermissions())

        val viewModel = vm()
        viewModel.load("sk-1")
        advanceUntilIdle()
        viewModel.grant(Principal(type = "user", id = "u1"), "role_editor")
        advanceUntilIdle()
        viewModel.revoke(Principal(type = "user", id = "u2"))
        advanceUntilIdle()

        assertThat(bodies).hasSize(2)
        assertThat(bodies.map { it.public }).containsExactly(null, null)
        assertThat(bodies.map { it.publicAccessRoleId }).containsExactly(null, null)
    }

    @Test
    fun `setPublic shows the public state the server reports after the change`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(emptyList())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returnsMany listOf(
            Result.Success(ResourcePermissions()),
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "role_viewer")),
        )
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())

        val viewModel = vm()
        viewModel.load("sk-1")
        advanceUntilIdle()
        viewModel.setPublic(enabled = true, accessRoleId = "role_viewer")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isPublic).isTrue()
        assertThat(viewModel.uiState.value.publicAccessRoleId).isEqualTo("role_viewer")
    }

    @Test
    fun `applyUpdate surfaces error on failure`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(emptyList())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Error(message = "denied")

        val viewModel = vm()
        viewModel.load("sk-1")
        advanceUntilIdle()
        viewModel.revoke(Principal(type = "user", id = "u1"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.updateFailed).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("denied")
    }

    @Test
    fun `mutation before load is a no-op (no skillId)`() = runTest(testDispatcher) {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.grant(Principal(type = "user", id = "u1"), "role_editor")
        advanceUntilIdle()
        coVerify(exactly = 0) {
            permissionsRepository.updateResourcePermissions(any(), any(), any())
        }
    }

    // --- Sharing sheet ---

    private val viewer = AccessRole(accessRoleId = "skill_viewer", name = "com_ui_role_viewer", permissionBits = 1)
    private val editor = AccessRole(accessRoleId = "skill_editor", name = "com_ui_role_editor", permissionBits = 3)
    private val owner = AccessRole(accessRoleId = "skill_owner", name = "com_ui_role_owner", permissionBits = 15)

    private fun TestScope.loadedVm(
        roles: Result<List<AccessRole>> = Result.Success(listOf(owner, editor, viewer)),
        grants: ResourcePermissions = ResourcePermissions(),
    ): SkillAclViewModel {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns roles
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Success(grants)
        val viewModel = vm()
        viewModel.setCanShare("sk-1", true)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `a failed roles fetch disables adding until a retry loads them`() = runTest(testDispatcher) {
        val viewModel = loadedVm(roles = Result.Error(message = "500"))
        assertThat(viewModel.uiState.value.rolesFailed).isTrue()
        assertThat(viewModel.uiState.value.canGrant).isFalse()

        viewModel.add(Principal(type = "user", id = "u1"))
        advanceUntilIdle()
        coVerify(exactly = 0) { permissionsRepository.updateResourcePermissions(any(), any(), any()) }

        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(listOf(viewer))
        viewModel.retryLoad()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rolesFailed).isFalse()
        assertThat(viewModel.uiState.value.canGrant).isTrue()
    }

    @Test
    fun `a failed grants fetch is reported, not shown as nobody`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.SKILL) } returns Result.Success(listOf(viewer))
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Error(message = "503")
        val viewModel = vm()
        viewModel.setCanShare("sk-1", true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.loaded).isFalse()

        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(principals = listOf(Principal(type = "user", id = "u1"))))
        viewModel.openSheet()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        assertThat(viewModel.uiState.value.principals).hasSize(1)
    }

    @Test
    fun `a failed search is not the same as no matches`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(sharerWithPicker)
        val viewModel = loadedVm()
        coEvery { permissionsRepository.searchPrincipals("sam", any()) } returns Result.Error(message = "timeout")
        viewModel.onSearchQueryChanged("sam")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchFailed).isTrue()
        assertThat(viewModel.uiState.value.searchResults).isEmpty()

        coEvery { permissionsRepository.searchPrincipals("sam", any()) } returns Result.Success(emptyList())
        viewModel.retrySearch()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchFailed).isFalse()
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
    }

    /** The server 400s a trimmed query under 2 characters; that must not read as a failed search. */
    @Test
    fun `a query too short for the server is never sent and is not a failure`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(sharerWithPicker)
        val viewModel = loadedVm()
        coEvery { permissionsRepository.searchPrincipals(any(), any()) } returns Result.Error(message = "400")

        viewModel.onSearchQueryChanged("a")
        advanceUntilIdle()
        viewModel.onSearchQueryChanged(" b ")
        advanceUntilIdle()
        viewModel.retrySearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { permissionsRepository.searchPrincipals(any(), any()) }
        assertThat(viewModel.uiState.value.searchFailed).isFalse()
        assertThat(viewModel.uiState.value.isSearching).isFalse()
        assertThat(viewModel.uiState.value.isQuerySearchable).isFalse()
    }

    @Test
    fun `a search sends the trimmed query`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(sharerWithPicker)
        val viewModel = loadedVm()
        coEvery { permissionsRepository.searchPrincipals("sam", any()) } returns
            Result.Success(listOf(Principal(type = "user", id = "u2", name = "Sam")))

        viewModel.onSearchQueryChanged("  sam ")
        advanceUntilIdle()

        coVerify(exactly = 1) { permissionsRepository.searchPrincipals("sam", any()) }
        assertThat(viewModel.uiState.value.searchResults.map { it.id }).containsExactly("u2")
    }

    @Test
    fun `add starts at the viewer role whatever order the server lists roles in`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        val body = slot<UpdateResourcePermissionsRequest>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(body)) } returns
            Result.Success(ResourcePermissions())
        viewModel.add(Principal(type = "user", id = "u1"))
        advanceUntilIdle()
        assertThat(body.captured.updated.single().accessRoleId).isEqualTo("skill_viewer")
        assertThat(body.captured.removed).isEmpty()
    }

    /** Resending `public: true` would run SHARE_PUBLIC and 403 a role without it. */
    @Test
    fun `changeRole re-grants the principal at the new role and leaves public out of the body`() = runTest(testDispatcher) {
        val ada = Principal(type = "user", id = "u1", name = "Ada", accessRoleId = "skill_viewer")
        val viewModel = loadedVm(
            grants = ResourcePermissions(principals = listOf(ada), public = true, publicAccessRoleId = "skill_viewer"),
        )
        val body = slot<UpdateResourcePermissionsRequest>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(body)) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Success(
            ResourcePermissions(
                principals = listOf(ada.copy(accessRoleId = "skill_editor")),
                public = true,
                publicAccessRoleId = "skill_viewer",
            ),
        )

        viewModel.changeRole(ada, "skill_viewer")
        advanceUntilIdle()
        coVerify(exactly = 0) { permissionsRepository.updateResourcePermissions(any(), any(), any()) }

        viewModel.changeRole(ada, "skill_editor")
        advanceUntilIdle()
        assertThat(body.captured.updated.single()).isEqualTo(ada.copy(accessRoleId = "skill_editor"))
        assertThat(body.captured.public).isNull()
        assertThat(body.captured.publicAccessRoleId).isNull()
        assertThat(viewModel.uiState.value.principals.single().accessRoleId).isEqualTo("skill_editor")
        assertThat(viewModel.uiState.value.isPublic).isTrue()
    }

    @Test
    fun `add and revoke leave public out of the body on a public skill`() = runTest(testDispatcher) {
        val ada = Principal(type = "user", id = "u1", name = "Ada", accessRoleId = "skill_viewer")
        val viewModel = loadedVm(
            grants = ResourcePermissions(principals = listOf(ada), public = true, publicAccessRoleId = "skill_viewer"),
        )
        val bodies = mutableListOf<UpdateResourcePermissionsRequest>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(bodies)) } returns
            Result.Success(ResourcePermissions())
        viewModel.add(Principal(type = "user", id = "u2"))
        advanceUntilIdle()
        viewModel.revoke(ada)
        advanceUntilIdle()
        assertThat(bodies).hasSize(2)
        bodies.forEach {
            assertThat(it.public).isNull()
            assertThat(it.publicAccessRoleId).isNull()
        }
    }

    /** `false` is the only thing that revokes public access on the server. */
    @Test
    fun `turning public off sends public false`() = runTest(testDispatcher) {
        val viewModel = loadedVm(grants = ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer"))
        val body = slot<UpdateResourcePermissionsRequest>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(body)) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(public = false))
        viewModel.setPublic(false)
        advanceUntilIdle()
        assertThat(body.captured.public).isFalse()
        assertThat(body.captured.publicAccessRoleId).isNull()
        assertThat(viewModel.uiState.value.isPublic).isFalse()
    }

    /** The PUT answers with only what it granted; the sheet keeps everyone else. */
    @Test
    fun `after a grant the sheet shows every grant the server holds, not the PUT body`() = runTest(testDispatcher) {
        val bea = Principal(type = "user", id = "u2", accessRoleId = "skill_viewer")
        val cal = Principal(type = "group", id = "g1", accessRoleId = "skill_editor")
        val viewModel = loadedVm(
            grants = ResourcePermissions(principals = listOf(bea, cal), public = true, publicAccessRoleId = "skill_viewer"),
        )
        val ada = Principal(type = "user", id = "u1")
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Success(
            ResourcePermissions(
                principals = listOf(bea, cal, ada.copy(accessRoleId = "skill_viewer")),
                public = true,
                publicAccessRoleId = "skill_viewer",
            ),
        )
        viewModel.add(ada)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.principals.map { it.id }).containsExactly("u2", "g1", "u1")
        assertThat(viewModel.uiState.value.isPublic).isTrue()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
    }

    @Test
    fun `controls stay waiting until the grants are read back`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        val readBack = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } coAnswers { readBack.await() }
        viewModel.setPublic(true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isUpdating).isTrue()
        readBack.complete(Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer")))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
        assertThat(viewModel.uiState.value.isPublic).isTrue()
    }

    /** A resume's GET started before a change can't land over it. */
    @Test
    fun `a grants fetch started before a change is dropped`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        val stale = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } coAnswers { stale.await() }
        viewModel.load("sk-1")
        advanceUntilIdle()

        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer"))
        viewModel.setPublic(true)
        advanceUntilIdle()
        stale.complete(Result.Success(ResourcePermissions(public = false)))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isPublic).isTrue()
    }

    @Test
    fun `a grants fetch asked for while a change is in flight waits for its read-back`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        val put = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } coAnswers { put.await() }
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer"))
        viewModel.setPublic(true)
        advanceUntilIdle()
        viewModel.load("sk-1")
        advanceUntilIdle()
        // Only the first load's GET so far; the resume's is skipped.
        coVerify(exactly = 1) { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") }

        put.complete(Result.Success(ResourcePermissions()))
        advanceUntilIdle()
        coVerify(exactly = 2) { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") }
        assertThat(viewModel.uiState.value.isPublic).isTrue()
    }

    @Test
    fun `turning public on with no role chosen uses the viewer default`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        val body = slot<UpdateResourcePermissionsRequest>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", capture(body)) } returns
            Result.Success(ResourcePermissions(public = true, publicAccessRoleId = "skill_viewer"))
        viewModel.setPublic(true)
        advanceUntilIdle()
        assertThat(body.captured.public).isTrue()
        assertThat(body.captured.publicAccessRoleId).isEqualTo("skill_viewer")
    }

    @Test
    fun `one change at a time - a second tap while the first is in flight is dropped`() = runTest(testDispatcher) {
        val viewModel = loadedVm()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        viewModel.add(Principal(type = "user", id = "u1"))
        viewModel.add(Principal(type = "user", id = "u2"))
        assertThat(viewModel.uiState.value.isUpdating).isTrue()
        advanceUntilIdle()
        coVerify(exactly = 1) { permissionsRepository.updateResourcePermissions(any(), any(), any()) }
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
    }

    @Test
    fun `the sheet opens only when sharing is allowed, and closing it clears the search`() = runTest(testDispatcher) {
        val gated = vm()
        gated.openSheet()
        assertThat(gated.uiState.value.showSheet).isFalse()

        every { roleRepository.userPermissions } returns MutableStateFlow(sharerWithPicker)
        val viewModel = loadedVm()
        viewModel.openSheet()
        viewModel.onSearchQueryChanged("sam")
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("sam")
        viewModel.closeSheet()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSheet).isFalse()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        coVerify(exactly = 0) { permissionsRepository.searchPrincipals(any(), any()) }
    }

    @Test
    fun `without the people picker the search is off and grants stay changeable`() = runTest(testDispatcher) {
        // USER's upstream default: SKILLS.SHARE given by an admin, PEOPLE_PICKER left all false.
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(
                name = "USER",
                permissions = mapOf(
                    "SKILLS" to mapOf("USE" to true, "CREATE" to true, "SHARE" to true, "SHARE_PUBLIC" to false),
                    "PEOPLE_PICKER" to mapOf("VIEW_USERS" to false, "VIEW_GROUPS" to false, "VIEW_ROLES" to false),
                ),
            ),
        )
        val sam = Principal(type = "user", id = "u2", name = "Sam", accessRoleId = "skill_viewer")
        val viewModel = loadedVm(grants = ResourcePermissions(principals = listOf(sam)))
        assertThat(viewModel.uiState.value.canSearchPeople).isFalse()

        viewModel.openSheet()
        viewModel.onSearchQueryChanged("sam")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        assertThat(viewModel.uiState.value.searchFailed).isFalse()
        coVerify(exactly = 0) { permissionsRepository.searchPrincipals(any(), any()) }
        // Changing an existing grant needs no search.
        assertThat(viewModel.uiState.value.canGrant).isTrue()
    }

    @Test
    fun `any one people picker view opens the search, and a null role keeps it closed`() = runTest(testDispatcher) {
        assertThat(vm().also { advanceUntilIdle() }.uiState.value.canSearchPeople).isFalse()

        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "USER", permissions = mapOf("PEOPLE_PICKER" to mapOf("VIEW_ROLES" to true))),
        )
        val viewModel = vm()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canSearchPeople).isTrue()

        // The server lets ADMIN search whatever its PEOPLE_PICKER map says.
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "ADMIN", permissions = mapOf("PEOPLE_PICKER" to mapOf("VIEW_USERS" to false))),
        )
        val admin = vm()
        advanceUntilIdle()
        assertThat(admin.uiState.value.canSearchPeople).isTrue()
    }

    @Test
    fun `search results leave out people who already have access`() {
        val ada = Principal(type = "user", id = "u1", name = "Ada", accessRoleId = "skill_viewer")
        val state = SkillAclUiState(
            principals = listOf(ada),
            searchResults = listOf(ada.copy(accessRoleId = null), Principal(type = "group", id = "u1", name = "Ops")),
        )
        assertThat(state.newSearchResults().map { it.name }).containsExactly("Ops")
    }

    @Test
    fun `a search whose every result already has access says so, not that no one matched`() {
        val ada = Principal(type = "user", id = "u1", name = "Ada", accessRoleId = "skill_viewer")
        val found = SkillAclUiState(principals = listOf(ada), searchResults = listOf(ada.copy(accessRoleId = null)))
        assertThat(found.newSearchResults()).isEmpty()
        assertThat(found.allResultsAlreadyShared).isTrue()

        assertThat(found.copy(searchResults = emptyList()).allResultsAlreadyShared).isFalse()
    }

    @Test
    fun `a grant can still be removed when the roles failed to load`() {
        val ada = Principal(type = "user", id = "u1", name = "Ada", accessRoleId = "skill_viewer")
        val state = SkillAclUiState(loaded = true, principals = listOf(ada), rolesFailed = true)
        assertThat(state.canGrant).isFalse()
        assertThat(state.canRevoke).isTrue()
        assertThat(state.copy(isUpdating = true).canRevoke).isFalse()
    }

    // --- a change that landed but couldn't be read back ---

    private val ada = Principal(type = "user", id = "u-ada", name = "Ada", accessRoleId = "skill_viewer")
    private val bob = Principal(type = "user", id = "u-bob", name = "Bob", accessRoleId = "skill_viewer")

    @Test
    fun `a revoke whose read-back fails drops the person and is not reported as a failed load`() = runTest(testDispatcher) {
        val viewModel = loadedVm(grants = ResourcePermissions(principals = listOf(ada, bob)))
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Error(message = "timeout")

        viewModel.revoke(bob)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.principals).containsExactly(ada)
        assertThat(state.loadFailed).isFalse()
        assertThat(state.updateFailed).isFalse()
        assertThat(state.isUpdating).isFalse()
    }

    @Test
    fun `an add or a public change whose read-back fails still shows what the server took`() = runTest(testDispatcher) {
        val viewModel = loadedVm(grants = ResourcePermissions(principals = listOf(ada)))
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Success(ResourcePermissions())
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns Result.Error(message = "timeout")

        viewModel.add(bob.copy(accessRoleId = null))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.principals).containsExactly(ada, bob).inOrder()

        viewModel.changeRole(ada, "skill_editor")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.principals.first().accessRoleId).isEqualTo("skill_editor")

        viewModel.setPublic(true)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isPublic).isTrue()
        assertThat(viewModel.uiState.value.publicAccessRoleId).isEqualTo("skill_viewer")
        assertThat(viewModel.uiState.value.loadFailed).isFalse()
    }

    // --- a failed change that cut a grants fetch short ---

    @Test
    fun `a failed change that cut a grants fetch short fetches them again`() = runTest(testDispatcher) {
        val viewModel = loadedVm(grants = ResourcePermissions(principals = listOf(ada)))
        val resumed = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } coAnswers { resumed.await() }
        viewModel.load("sk-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.SKILL, "sk-1", any()) } returns
            Result.Error(message = "denied")
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") } returns
            Result.Success(ResourcePermissions(principals = listOf(ada, bob)))
        viewModel.setPublic(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.updateFailed).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.principals).containsExactly(ada, bob)
        coVerify(exactly = 3) { permissionsRepository.getResourcePermissions(ResourceType.SKILL, "sk-1") }
    }
}
