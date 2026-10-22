package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.SkillViewer
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentAclViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val permissionsRepository = mockk<PermissionsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val bodies = mutableListOf<UpdateResourcePermissionsRequest>()

    private val ada = Principal(type = "user", id = "u-ada", name = "Ada", accessRoleId = "agent_viewer")
    private val bob = Principal(type = "user", id = "u-bob", name = "Bob")

    /** What the server holds: a public agent shared with Ada. */
    private var serverGrants = ResourcePermissions(
        principals = listOf(ada),
        public = true,
        publicAccessRoleId = "agent_viewer",
    )

    /** A USER whose admin turned the people picker and agent sharing (public too) on. */
    private val sharingUser = UserRolePermissions(
        name = "USER",
        permissions = mapOf(
            "PEOPLE_PICKER" to mapOf("VIEW_USERS" to true),
            "AGENTS" to mapOf("USE" to true, "CREATE" to true, "SHARE" to true, "SHARE_PUBLIC" to true),
        ),
    )
    private val role = MutableStateFlow<UserRolePermissions?>(sharingUser)

    private val viewer = AccessRole(accessRoleId = "agent_viewer", name = "Viewer")

    /** The signed-in user: not the author, not an admin. */
    private var signedIn = User(id = "u-me", email = "me@example.com")

    /** The viewer's ACL bits per agent `_id`: an owner's 15 (VIEW|EDIT|DELETE|SHARE) on agent-1. */
    private var effectiveBits = mapOf("agent-1" to 15)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns role
        coEvery { userRepository.getUser() } answers { Result.Success(signedIn) }
        coEvery { permissionsRepository.getAllEffectivePermissions(ResourceType.AGENT) } answers { Result.Success(effectiveBits) }
        coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } returns Result.Success(listOf(viewer))
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } answers {
            Result.Success(serverGrants)
        }
        // The PUT answers `{ message, results }`, which decodes to an empty ResourcePermissions.
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.AGENT, "agent-1", capture(bodies)) } returns
            Result.Success(ResourcePermissions())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun newViewModel() = AgentAclViewModel(permissionsRepository, roleRepository, userRepository)

    private fun TestScope.loaded(): AgentAclViewModel {
        val viewModel = newViewModel()
        viewModel.load("agent-1", AUTHOR)
        advanceUntilIdle()
        return viewModel
    }

    /** A cached `public: false` on a grant or revoke would make a public agent private. */
    @Test
    fun `grants and revokes on a public agent leave public out of the body`() = runTest(testDispatcher) {
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.isPublic).isTrue()

        viewModel.grant(bob, "agent_viewer")
        advanceUntilIdle()
        viewModel.revoke(ada)
        advanceUntilIdle()

        assertThat(bodies).hasSize(2)
        bodies.forEach { body ->
            assertThat(body.public).isNull()
            assertThat(body.publicAccessRoleId).isNull()
        }
        assertThat(bodies[0].updated).containsExactly(bob.copy(accessRoleId = "agent_viewer"))
        assertThat(bodies[1].removed).containsExactly(ada)
    }

    @Test
    fun `after a change the grants are read back, not copied from the PUT answer`() = runTest(testDispatcher) {
        val viewModel = loaded()
        serverGrants = serverGrants.copy(principals = listOf(ada, bob.copy(accessRoleId = "agent_viewer")))

        viewModel.grant(bob, "agent_viewer")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isPublic).isTrue()
        assertThat(state.publicAccessRoleId).isEqualTo("agent_viewer")
        assertThat(state.principals.map { it.id }).containsExactly("u-ada", "u-bob")
        coVerify(exactly = 2) { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") }
    }

    @Test
    fun `turning public off sends false, and only setPublic sends it`() = runTest(testDispatcher) {
        val viewModel = loaded()
        serverGrants = serverGrants.copy(public = false, publicAccessRoleId = null)

        viewModel.setPublic(enabled = false, accessRoleId = null)
        advanceUntilIdle()

        assertThat(bodies.single().public).isFalse()
        assertThat(viewModel.uiState.value.isPublic).isFalse()
    }

    @Test
    fun `a second change while one is in flight is refused, so two can't land out of order`() = runTest(testDispatcher) {
        val viewModel = loaded()
        val put = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.AGENT, "agent-1", capture(bodies)) } coAnswers {
            put.await()
        }

        viewModel.revoke(ada)
        runCurrent()
        assertThat(viewModel.uiState.value.isUpdating).isTrue()
        viewModel.revoke(bob)
        runCurrent()
        assertThat(bodies).hasSize(1)

        serverGrants = serverGrants.copy(principals = emptyList())
        put.complete(Result.Success(ResourcePermissions()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
        assertThat(viewModel.uiState.value.principals).isEmpty()
    }

    @Test
    fun `a grants fetch from before a change can't land after it`() = runTest(testDispatcher) {
        val viewModel = loaded()
        val staleGet = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } coAnswers {
            staleGet.await()
        } andThenAnswer { Result.Success(serverGrants) }

        // A reload starts; the user revokes before it answers.
        viewModel.load("agent-1", AUTHOR)
        runCurrent()
        serverGrants = serverGrants.copy(principals = emptyList())
        viewModel.revoke(ada)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.principals).isEmpty()

        staleGet.complete(Result.Success(ResourcePermissions(principals = listOf(ada))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.principals).isEmpty()
    }

    @Test
    fun `a change that landed is not reported as failed when reading it back fails`() = runTest(testDispatcher) {
        val viewModel = loaded()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } returns
            Result.Error(message = "timeout")

        viewModel.revoke(ada)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.isUpdating).isFalse()
        // The server took the change, so the list shows it.
        assertThat(state.principals).isEmpty()
        assertThat(state.isPublic).isTrue()
    }

    @Test
    fun `a failed read-back after a grant or public change still shows what the server took`() = runTest(testDispatcher) {
        val viewModel = loaded()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } returns
            Result.Error(message = "timeout")

        viewModel.grant(bob, "agent_viewer")
        advanceUntilIdle()
        viewModel.setPublic(enabled = false, accessRoleId = null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
        assertThat(state.principals).containsExactly(ada, bob.copy(accessRoleId = "agent_viewer")).inOrder()
        assertThat(state.isPublic).isFalse()
        assertThat(state.publicAccessRoleId).isNull()
    }

    @Test
    fun `a search too short for the server is not sent, and a longer one goes out trimmed`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.searchPrincipals(any(), any()) } returns Result.Success(listOf(bob))
        val viewModel = loaded()

        viewModel.onSearchQueryChanged(" b ")
        advanceUntilIdle()
        coVerify(exactly = 0) { permissionsRepository.searchPrincipals(any(), any()) }
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        assertThat(viewModel.uiState.value.isSearching).isFalse()

        viewModel.onSearchQueryChanged("  bob ")
        advanceUntilIdle()
        coVerify(exactly = 1) { permissionsRepository.searchPrincipals("bob", any()) }
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("  bob ")
        assertThat(viewModel.uiState.value.searchResults).containsExactly(bob)
    }

    // --- a change during the first load ---

    @Test
    fun `a change made while the first load runs still gets the roles, and a public turn-on carries one`() =
        runTest(testDispatcher) {
            val roles = CompletableDeferred<Result<List<AccessRole>>>()
            coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } coAnswers { roles.await() }
            serverGrants = serverGrants.copy(public = false, publicAccessRoleId = null)
            val viewModel = newViewModel()
            viewModel.load("agent-1", AUTHOR)
            advanceUntilIdle()

            // No role known yet: turning public on is refused rather than sent without one.
            viewModel.setPublic(enabled = true, accessRoleId = null)
            advanceUntilIdle()
            assertThat(bodies).isEmpty()

            // A revoke lands while the roles are still on their way; they must still arrive.
            viewModel.revoke(ada)
            advanceUntilIdle()
            roles.complete(Result.Success(listOf(viewer)))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.availableRoles).containsExactly(viewer)
            assertThat(viewModel.uiState.value.canGrant).isTrue()

            viewModel.setPublic(enabled = true, accessRoleId = null)
            advanceUntilIdle()
            assertThat(bodies.last().public).isTrue()
            assertThat(bodies.last().publicAccessRoleId).isEqualTo("agent_viewer")
        }

    @Test
    fun `a failed change that cut the first grants fetch short fetches them again`() = runTest(testDispatcher) {
        val firstGet = CompletableDeferred<Result<ResourcePermissions>>()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } coAnswers {
            firstGet.await()
        } andThenAnswer { Result.Success(serverGrants) }
        coEvery { permissionsRepository.updateResourcePermissions(ResourceType.AGENT, "agent-1", any()) } returns
            Result.Error(message = "denied")
        val viewModel = newViewModel()
        viewModel.load("agent-1", AUTHOR)
        runCurrent()

        viewModel.revoke(bob)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loaded).isTrue()
        assertThat(state.principals).containsExactly(ada)
        assertThat(state.isPublic).isTrue()
        assertThat(state.error).isEqualTo("denied")
    }

    // --- the people search ---

    @Test
    fun `a new query drops the last results at once, not after the debounce`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.searchPrincipals("alice", any()) } returns Result.Success(listOf(ada))
        val viewModel = loaded()
        viewModel.onSearchQueryChanged("alice")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchResults).containsExactly(ada)

        viewModel.onSearchQueryChanged("bob")
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        assertThat(viewModel.uiState.value.isSearching).isTrue()
    }

    @Test
    fun `a failed search shows no one from the last query and says it failed`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.searchPrincipals("alice", any()) } returns Result.Success(listOf(ada))
        coEvery { permissionsRepository.searchPrincipals("bob", any()) } returns Result.Error(message = "offline") andThen
            Result.Success(listOf(bob))
        val viewModel = loaded()
        viewModel.onSearchQueryChanged("alice")
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("bob")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        assertThat(viewModel.uiState.value.searchFailed).isTrue()
        assertThat(viewModel.uiState.value.isSearching).isFalse()

        viewModel.retrySearch()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchResults).containsExactly(bob)
        assertThat(viewModel.uiState.value.searchFailed).isFalse()
    }

    @Test
    fun `a search still on its way when the dialog closes can't fill the reopened one`() = runTest(testDispatcher) {
        val search = CompletableDeferred<Result<List<Principal>>>()
        coEvery { permissionsRepository.searchPrincipals("al", any()) } coAnswers { search.await() }
        val viewModel = loaded()
        viewModel.openGrantDialog()
        viewModel.onSearchQueryChanged("al")
        advanceUntilIdle()
        viewModel.dismissGrantDialog()
        viewModel.openGrantDialog()

        search.complete(Result.Success(listOf(ada)))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        assertThat(viewModel.uiState.value.searchResults).isEmpty()
        assertThat(viewModel.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `without the people picker no search is offered or sent`() = runTest(testDispatcher) {
        // A stock USER: PEOPLE_PICKER all false, so the server refuses every search with a 403.
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("PEOPLE_PICKER" to mapOf("VIEW_USERS" to false, "VIEW_GROUPS" to false, "VIEW_ROLES" to false)),
        )
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.canSearchPeople).isFalse()

        viewModel.onSearchQueryChanged("alice")
        advanceUntilIdle()
        coVerify(exactly = 0) { permissionsRepository.searchPrincipals(any(), any()) }
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()

        role.value = UserRolePermissions(name = "ADMIN")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canSearchPeople).isTrue()
    }

    // --- the role's AGENTS.SHARE and SHARE_PUBLIC ---

    /** The stock USER role (`roles.ts`): agents usable and creatable, not shareable. */
    private val stockUser = UserRolePermissions(
        name = "USER",
        permissions = mapOf(
            "PEOPLE_PICKER" to mapOf("VIEW_USERS" to true),
            "AGENTS" to mapOf("USE" to true, "CREATE" to true, "SHARE" to false, "SHARE_PUBLIC" to false),
        ),
    )

    @Test
    fun `without AGENTS SHARE nothing is fetched or sent, and the grants load once the role allows it`() =
        runTest(testDispatcher) {
            role.value = stockUser
            val viewModel = loaded()

            assertThat(viewModel.uiState.value.canShare).isFalse()
            coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }
            viewModel.grant(bob, "agent_viewer")
            viewModel.revoke(ada)
            viewModel.setPublic(enabled = false, accessRoleId = null)
            viewModel.openGrantDialog()
            advanceUntilIdle()
            assertThat(bodies).isEmpty()
            assertThat(viewModel.uiState.value.showGrantDialog).isFalse()

            role.value = sharingUser
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.canShare).isTrue()
            assertThat(viewModel.uiState.value.loaded).isTrue()
            assertThat(viewModel.uiState.value.principals).containsExactly(ada)
        }

    @Test
    fun `without AGENTS SHARE_PUBLIC public access can't be turned on`() = runTest(testDispatcher) {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("AGENTS" to mapOf("SHARE" to true, "SHARE_PUBLIC" to false)),
        )
        serverGrants = serverGrants.copy(public = false, publicAccessRoleId = null)
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.canShare).isTrue()
        assertThat(viewModel.uiState.value.canSharePublic).isFalse()

        viewModel.setPublic(enabled = true, accessRoleId = null)
        advanceUntilIdle()
        assertThat(bodies).isEmpty()

        viewModel.grant(bob, "agent_viewer")
        advanceUntilIdle()
        assertThat(bodies).hasSize(1)
        assertThat(bodies.single().public).isNull()
    }

    // --- the default role ---

    @Test
    fun `public access starts at the viewer role even when the server lists owner first`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } returns Result.Success(
            listOf(
                AccessRole(accessRoleId = "agent_owner", name = "com_ui_role_owner", permissionBits = 15),
                AccessRole(accessRoleId = "agent_editor", name = "com_ui_role_editor", permissionBits = 3),
                AccessRole(accessRoleId = "agent_viewer", name = "com_ui_role_viewer", permissionBits = 1),
            ),
        )
        serverGrants = serverGrants.copy(public = false, publicAccessRoleId = null)
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.defaultRoleId).isEqualTo("agent_viewer")

        viewModel.setPublic(enabled = true, accessRoleId = null)
        advanceUntilIdle()
        assertThat(bodies.single().publicAccessRoleId).isEqualTo("agent_viewer")
    }

    // --- the dialog's roles retry ---

    @Test
    fun `the grant dialog's retry refetches roles that failed`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } returns Result.Error(RuntimeException("down"), "down")
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.rolesFailed).isTrue()

        coEvery { permissionsRepository.getResourceRoles(ResourceType.AGENT) } returns Result.Success(listOf(viewer))
        viewModel.retryRoles()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rolesFailed).isFalse()
        assertThat(viewModel.uiState.value.canGrant).isTrue()
    }

    // --- may the viewer share THIS agent ---

    @Test
    fun `an editor without the agent's SHARE bit gets no section and no grants fetch`() = runTest(testDispatcher) {
        // agent_editor: VIEW|EDIT, no SHARE. The role may share, but the server checks the ACL bit too.
        effectiveBits = mapOf("agent-1" to 3)
        val viewModel = loaded()

        assertThat(viewModel.uiState.value.canShare).isFalse()
        coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }
        viewModel.grant(bob, "agent_viewer")
        viewModel.openGrantDialog()
        advanceUntilIdle()
        assertThat(bodies).isEmpty()
        assertThat(viewModel.uiState.value.showGrantDialog).isFalse()
    }

    @Test
    fun `the author and an admin may share without the bit, the bit alone needs the role`() {
        val me = SkillViewer(userId = "u-me", isAdmin = false)
        val admin = me.copy(isAdmin = true)
        assertThat(resolveAgentCanShare(sharingUser, me, authorId = "u-me", agentBits = 0)).isTrue()
        assertThat(resolveAgentCanShare(sharingUser, admin, authorId = AUTHOR, agentBits = 0)).isTrue()
        assertThat(resolveAgentCanShare(stockUser, admin, authorId = AUTHOR, agentBits = 0)).isTrue()
        assertThat(resolveAgentCanShare(sharingUser, me, authorId = AUTHOR, agentBits = 8)).isTrue()
        assertThat(resolveAgentCanShare(stockUser, me, authorId = "u-me", agentBits = 15)).isFalse()
        // A user who failed to load is neither author nor admin.
        assertThat(resolveAgentCanShare(sharingUser, null, authorId = "u-me", agentBits = 3)).isFalse()
    }

    @Test
    fun `the grants are asked for by the agent's database id, once every share input has answered`() =
        runTest(testDispatcher) {
            val user = CompletableDeferred<Result<User>>()
            coEvery { userRepository.getUser() } coAnswers { user.await() }
            val viewModel = newViewModel()
            viewModel.load("agent-1", AUTHOR)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.canShare).isFalse()
            coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }

            user.complete(Result.Success(signedIn))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.canShare).isTrue()
            coVerify(exactly = 1) { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") }
        }

    // --- a failed grants load ---

    @Test
    fun `a failed first grants load is reported with a retry, not shown as no grants`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } returns
            Result.Error(message = "offline") andThenAnswer { Result.Success(serverGrants) }
        val viewModel = loaded()

        val failed = viewModel.uiState.value
        assertThat(failed.loadFailed).isTrue()
        assertThat(failed.loaded).isFalse()
        assertThat(failed.error).isNull()

        viewModel.retryLoad()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertThat(state.loadFailed).isFalse()
        assertThat(state.loaded).isTrue()
        assertThat(state.principals).containsExactly(ada)
    }

    // --- the author's own grant ---

    @Test
    fun `the author's own grant can't be revoked, anyone else's can`() = runTest(testDispatcher) {
        val author = Principal(type = "user", id = AUTHOR, name = "Owner", accessRoleId = "agent_owner")
        serverGrants = serverGrants.copy(principals = listOf(author, ada))
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.isAuthor(author)).isTrue()
        assertThat(viewModel.uiState.value.isAuthor(ada)).isFalse()

        viewModel.revoke(author)
        advanceUntilIdle()
        assertThat(bodies).isEmpty()

        viewModel.revoke(ada)
        advanceUntilIdle()
        assertThat(bodies.single().removed).containsExactly(ada)
    }

    // --- a grant is an upsert, so the dialog can't offer anyone already listed ---

    @Test
    fun `the author and anyone already granted are neither offered nor granted from the dialog`() =
        runTest(testDispatcher) {
            val author = Principal(type = "user", id = AUTHOR, name = "Owner", accessRoleId = "agent_owner")
            serverGrants = serverGrants.copy(principals = listOf(author, ada))
            coEvery { permissionsRepository.searchPrincipals(any(), any()) } returns Result.Success(
                listOf(author.copy(accessRoleId = null), ada.copy(accessRoleId = null), bob, bob),
            )
            val viewModel = loaded()
            viewModel.openGrantDialog()
            viewModel.onSearchQueryChanged("ow")
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.newSearchResults()).containsExactly(bob)

            viewModel.grant(author, "agent_viewer")
            advanceUntilIdle()
            viewModel.grant(ada, "agent_editor")
            advanceUntilIdle()
            assertThat(bodies).isEmpty()

            viewModel.grant(bob, "agent_viewer")
            advanceUntilIdle()
            assertThat(bodies.single().updated).containsExactly(bob.copy(accessRoleId = "agent_viewer"))
        }

    // --- a change's read-back clears a failed load ---

    @Test
    fun `a change whose read-back lands shows the list, not the earlier load failure`() = runTest(testDispatcher) {
        val viewModel = loaded()
        coEvery { permissionsRepository.getResourcePermissions(ResourceType.AGENT, "agent-1") } returns
            Result.Error(message = "offline") andThenAnswer { Result.Success(serverGrants) }
        // A reload fails while the list from before is still loaded.
        viewModel.load("agent-1", AUTHOR)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.loaded).isTrue()

        serverGrants = serverGrants.copy(principals = listOf(ada, bob.copy(accessRoleId = "agent_viewer")))
        viewModel.grant(bob, "agent_viewer")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.loadFailed).isFalse()
        assertThat(state.principals.map { it.id }).containsExactly("u-ada", "u-bob")
    }

    // --- a failed share check is said and retried, not final ---

    @Test
    fun `a failed user load is reported with a retry that brings sharing back`() = runTest(testDispatcher) {
        signedIn = User(id = AUTHOR, email = "author@example.com")
        effectiveBits = emptyMap()
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline") andThenAnswer {
            Result.Success(signedIn)
        }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.canShare).isFalse()
        assertThat(viewModel.uiState.value.shareCheckFailed).isTrue()

        viewModel.retryShareCheck()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.canShare).isTrue()
        assertThat(state.shareCheckFailed).isFalse()
        assertThat(state.loaded).isTrue()
    }

    @Test
    fun `a failed bits load is reported with a retry that refetches them`() = runTest(testDispatcher) {
        coEvery { permissionsRepository.getAllEffectivePermissions(ResourceType.AGENT) } returns
            Result.Error(message = "offline") andThenAnswer { Result.Success(effectiveBits) }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.canShare).isFalse()
        assertThat(viewModel.uiState.value.shareCheckFailed).isTrue()

        viewModel.retryShareCheck()
        // The failure stays up, marked as being checked, until the retry answers.
        assertThat(viewModel.uiState.value.shareCheckFailed).isTrue()
        assertThat(viewModel.uiState.value.isCheckingShare).isTrue()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canShare).isTrue()
        assertThat(viewModel.uiState.value.shareCheckFailed).isFalse()
        assertThat(viewModel.uiState.value.isCheckingShare).isFalse()
        coVerify(exactly = 2) { permissionsRepository.getAllEffectivePermissions(ResourceType.AGENT) }
    }

    @Test
    fun `a failed bits load without the role to share says nothing`() = runTest(testDispatcher) {
        role.value = stockUser
        coEvery { permissionsRepository.getAllEffectivePermissions(ResourceType.AGENT) } returns
            Result.Error(message = "offline")
        val viewModel = loaded()

        assertThat(viewModel.uiState.value.canShare).isFalse()
        assertThat(viewModel.uiState.value.shareCheckFailed).isFalse()
    }

    private companion object {
        const val AUTHOR = "u-author"
    }
}
