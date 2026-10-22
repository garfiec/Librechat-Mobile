package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillStatesUpdateResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.SkillAccess
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.response.DeleteSkillResponse
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repo = mockk<SkillsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val me = "65f0c0ffee0000000000abcd"
    private val someoneElse = "65f0c0ffee00000000002222"

    /** `GET /api/user` as the server sends it. */
    private fun user(id: String = me, role: String = "USER") = json.decodeFromString(
        User.serializer(),
        """{"id":"$id","name":"Ada","email":"ada@example.com","role":"$role","provider":"local"}""",
    )

    /** `GET /api/skills/:id` as `serializeSkill` sends it. */
    private fun skill(author: String = me, source: String = "inline") = json.decodeFromString(
        Skill.serializer(),
        """{"_id":"sk-1","name":"pdf-tools","description":"d","body":"b","author":"$author",""" +
            """"authorName":"Ada","version":1,"source":"$source","fileCount":0,"isPublic":false}""",
    )

    private val createAndShare = UserRolePermissions(
        name = "USER",
        permissions = mapOf("SKILLS" to mapOf("USE" to true, "CREATE" to true, "SHARE" to true)),
    )

    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(createAndShare)
        every { configRepository.startupConfig } returns startupConfig
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill())
        coEvery { repo.getSkillStates() } returns Result.Success(emptyMap())
        coEvery { repo.getSkillEffectivePermissions() } returns Result.Success(emptyMap())
        coEvery { userRepository.getUser() } returns Result.Success(user())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val saveNotices = SkillSaveNotices()

    private fun vm() = SkillDetailViewModel(repo, roleRepository, userRepository, configRepository, saveNotices, "sk-1")

    private fun TestScope.loaded(): SkillDetailViewModel {
        val viewModel = vm()
        viewModel.load()
        advanceUntilIdle()
        return viewModel
    }

    // --- clobber guard + override merge ---

    @Test
    fun `the first frame is loading, not a blank page`() = runTest(testDispatcher) {
        // Loading starts on the first resume; the frames before it draw the spinner.
        val state = vm().uiState.value
        assertThat(state.skill).isNull()
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `toggleActive does not persist when states never loaded (clobber guard)`() = runTest(testDispatcher) {
        // States fetch FAILS → snapshot stays null, Switch must not act.
        coEvery { repo.getSkillStates() } returns Result.Error(message = "boom")
        val viewModel = loaded()

        assertThat(viewModel.uiState.value.activeStateLoaded).isFalse()

        viewModel.toggleActive()
        advanceUntilIdle()

        // The full-replace POST must NOT fire on a null snapshot.
        coVerify(exactly = 0) { repo.updateSkillStates(any()) }
    }

    @Test
    fun `toggleActive merges into loaded snapshot without clobbering other overrides`() = runTest(testDispatcher) {
        coEvery { repo.getSkillStates() } returns Result.Success(mapOf("other-skill" to false))
        coEvery { repo.updateSkillStates(any()) } answers { SkillStatesUpdateResult.Success(firstArg()) }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        assertThat(viewModel.uiState.value.isActive).isTrue()

        viewModel.toggleActive()
        advanceUntilIdle()

        // Persisted map keeps the other skill's override and flips only sk-1.
        coVerify { repo.updateSkillStates(mapOf("other-skill" to false, "sk-1" to false)) }
        assertThat(viewModel.uiState.value.isActive).isFalse()
    }

    @Test
    fun `the toggle waits for the viewer, since the default decides store versus delete`() = runTest(testDispatcher) {
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        val viewModel = loaded()

        assertThat(viewModel.uiState.value.activeStateLoaded).isFalse()
        viewModel.toggleActive()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.updateSkillStates(any()) }
    }

    // --- the on/off state is shown only once known; a failed load offers Retry ---

    @Test
    fun `while the states map is in flight the state is neither loaded nor failed`() = runTest(testDispatcher) {
        val statesGate = CompletableDeferred<Result<Map<String, Boolean>>>()
        coEvery { repo.getSkillStates() } coAnswers { statesGate.await() }
        val viewModel = loaded()

        assertThat(viewModel.uiState.value.activeStateLoaded).isFalse()
        assertThat(viewModel.uiState.value.activeStateFailed).isFalse()

        statesGate.complete(Result.Success(emptyMap()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        assertThat(viewModel.uiState.value.isActive).isTrue()
    }

    @Test
    fun `a failed states fetch is reported, and Retry loads it`() = runTest(testDispatcher) {
        coEvery { repo.getSkillStates() } returns Result.Error(message = "boom")
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.activeStateFailed).isTrue()
        assertThat(viewModel.uiState.value.activeStateLoaded).isFalse()

        coEvery { repo.getSkillStates() } returns Result.Success(emptyMap())
        viewModel.retryActiveState()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeStateFailed).isFalse()
        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        // An owned skill is on by default: the row now shows On, never a guessed Off.
        assertThat(viewModel.uiState.value.isActive).isTrue()
    }

    @Test
    fun `a failed viewer fetch is reported, and Retry refetches the viewer`() = runTest(testDispatcher) {
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.activeStateFailed).isTrue()

        coEvery { userRepository.getUser() } returns Result.Success(user())
        viewModel.retryActiveState()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeStateFailed).isFalse()
        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        assertThat(viewModel.uiState.value.isActive).isTrue()
        // The states map had loaded; Retry refetches only what failed.
        coVerify(exactly = 1) { repo.getSkillStates() }
    }

    @Test
    fun `a user without an id is reported, and Retry refetches it`() = runTest(testDispatcher) {
        coEvery { userRepository.getUser() } returns Result.Success(user().copy(id = null, mongoId = null))
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.activeStateFailed).isTrue()

        coEvery { userRepository.getUser() } returns Result.Success(user())
        viewModel.retryActiveState()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        assertThat(viewModel.uiState.value.activeStateFailed).isFalse()
    }

    @Test
    fun `a failed refresh after the state loaded keeps it shown`() = runTest(testDispatcher) {
        val viewModel = loaded()
        coEvery { repo.getSkillStates() } returns Result.Error(message = "boom")
        viewModel.load()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()
        assertThat(viewModel.uiState.value.activeStateFailed).isFalse()
    }

    // --- the server's default, and deleting an override equal to it ---

    @Test
    fun `an owned skill defaults on`() = runTest(testDispatcher) {
        assertThat(loaded().uiState.value.isActive).isTrue()
    }

    @Test
    fun `a shared skill defaults off`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        assertThat(loaded().uiState.value.isActive).isFalse()
    }

    @Test
    fun `a shared skill defaults on when the server sets defaultActiveOnShare`() = runTest(testDispatcher) {
        startupConfig.value = json.decodeFromString(
            StartupConfig.serializer(),
            """{"interface":{"skills":{"use":true,"defaultActiveOnShare":true}}}""",
        )
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        assertThat(loaded().uiState.value.isActive).isTrue()
    }

    @Test
    fun `a deployment skill defaults on`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns
            Result.Success(skill(author = someoneElse, source = "deployment"))
        assertThat(loaded().uiState.value.isActive).isTrue()
    }

    @Test
    fun `toggling back to the default deletes the override`() = runTest(testDispatcher) {
        coEvery { repo.getSkillStates() } returns Result.Success(mapOf("sk-1" to false, "other" to true))
        coEvery { repo.updateSkillStates(any()) } answers { SkillStatesUpdateResult.Success(firstArg()) }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.isActive).isFalse()

        viewModel.toggleActive()
        advanceUntilIdle()

        coVerify { repo.updateSkillStates(mapOf("other" to true)) }
        assertThat(viewModel.uiState.value.isActive).isTrue()
    }

    @Test
    fun `the pruned map the server returns is adopted`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        // The server drops the key (e.g. the skill stopped being visible): the default applies.
        coEvery { repo.updateSkillStates(any()) } returns SkillStatesUpdateResult.Success(emptyMap())
        val viewModel = loaded()

        viewModel.toggleActive()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isActive).isFalse()
    }

    /** A second toggle while the first POST is in flight would race a full replace. */
    @Test
    fun `the toggle is refused while a save is in flight`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillStatesUpdateResult>()
        coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
        val viewModel = loaded()

        viewModel.toggleActive()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSavingActive).isTrue()
        assertThat(viewModel.uiState.value.isActive).isFalse()

        viewModel.toggleActive()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.updateSkillStates(any()) }

        gate.complete(SkillStatesUpdateResult.Success(mapOf("sk-1" to false)))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSavingActive).isFalse()
        assertThat(viewModel.uiState.value.isActive).isFalse()
    }

    @Test
    fun `a states fetch from before a toggle can't land after it and flip the switch back`() = runTest(testDispatcher) {
        // Shared and off: the toggle stores an explicit `true`.
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        val staleGet = CompletableDeferred<Result<Map<String, Boolean>>>()
        coEvery { repo.getSkillStates() } returns Result.Success(emptyMap()) coAndThen { staleGet.await() }
        coEvery { repo.updateSkillStates(any()) } answers { SkillStatesUpdateResult.Success(firstArg()) }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.isActive).isFalse()

        // A return from the editor starts a states GET; the user toggles before it answers.
        viewModel.load()
        advanceUntilIdle()
        viewModel.toggleActive()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isActive).isTrue()

        staleGet.complete(Result.Success(emptyMap()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isActive).isTrue()

        // The next toggle builds its body from the POST's map, not the stale one.
        viewModel.toggleActive()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isActive).isFalse()
    }

    @Test
    fun `a resume during a toggle doesn't start a states fetch`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillStatesUpdateResult>()
        coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
        val viewModel = loaded()

        viewModel.toggleActive()
        advanceUntilIdle()
        viewModel.load()
        advanceUntilIdle()
        gate.complete(SkillStatesUpdateResult.Success(mapOf("sk-1" to false)))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.getSkillStates() }
        assertThat(viewModel.uiState.value.isActive).isFalse()
    }

    /** The failure reaches the user once, not a state field nothing renders. */
    @Test
    fun `a failed toggle rolls back and reports once`() = runTest(testDispatcher) {
        coEvery { repo.updateSkillStates(any()) } returns SkillStatesUpdateResult.Error("nope")
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.toggleActive()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isActive).isTrue()
        assertThat(viewModel.uiState.value.isSavingActive).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(events).containsExactly(SkillDetailEvent.ActionFailed(SkillDetailAction.TOGGLE_ACTIVE, "nope"))
        collector.cancel()
    }

    /** The server's cap on overrides gets its own words, not its English sentence. */
    @Test
    fun `a toggle refused for too many overrides rolls back and says what to do`() = runTest(testDispatcher) {
        coEvery { repo.updateSkillStates(any()) } returns SkillStatesUpdateResult.TooManyOverrides
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.toggleActive()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isActive).isTrue()
        assertThat(viewModel.uiState.value.isSavingActive).isFalse()
        assertThat(events).containsExactly(SkillDetailEvent.TooManyOverrides)
        collector.cancel()
    }

    // --- delete ---

    @Test
    fun `a failed delete reports once and re-enables the action`() = runTest(testDispatcher) {
        coEvery { repo.deleteSkill("sk-1") } returns Result.Error(message = "403")
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isDeleting).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(events).containsExactly(SkillDetailEvent.ActionFailed(SkillDetailAction.DELETE, "403"))
        collector.cancel()
    }

    @Test
    fun `a delete in flight blocks a second one`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<DeleteSkillResponse>>()
        coEvery { repo.deleteSkill("sk-1") } coAnswers { gate.await() }
        val viewModel = loaded()

        viewModel.confirmDelete()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDeleting).isTrue()
        viewModel.confirmDelete()
        viewModel.requestDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.deleteSkill("sk-1") }
        assertThat(viewModel.uiState.value.showDeleteConfirm).isFalse()
        gate.complete(Result.Success(DeleteSkillResponse(id = "sk-1", deleted = true)))
        advanceUntilIdle()
    }

    /** The delete's landing pops the page; a back first would make that pop the list. */
    @Test
    fun `back is held while a delete is in flight, so only the delete pops the page`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Result<DeleteSkillResponse>>()
        coEvery { repo.deleteSkill("sk-1") } coAnswers { gate.await() }
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }

        viewModel.confirmDelete()
        advanceUntilIdle()
        viewModel.onBackRequested()
        advanceUntilIdle()
        assertThat(events).isEmpty()

        gate.complete(Result.Success(DeleteSkillResponse(id = "sk-1", deleted = true)))
        advanceUntilIdle()
        assertThat(events).containsExactly(SkillDetailEvent.Deleted)
        collector.cancel()
    }

    /** The page is still composed while the Deleted pop animates; a back then must not pop the list. */
    @Test
    fun `back after a successful delete emits nothing more`() = runTest(testDispatcher) {
        coEvery { repo.deleteSkill("sk-1") } returns Result.Success(DeleteSkillResponse(id = "sk-1", deleted = true))
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }

        viewModel.confirmDelete()
        advanceUntilIdle()
        viewModel.onBackRequested()
        viewModel.requestDelete()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillDetailEvent.Deleted)
        assertThat(viewModel.uiState.value.showDeleteConfirm).isFalse()
        collector.cancel()
    }

    /** Leaving would cancel the on/off POST, or let the list's states GET beat it. */
    @Test
    fun `back during an on-off save leaves only after the save lands`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillStatesUpdateResult>()
        coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }

        viewModel.toggleActive()
        advanceUntilIdle()
        viewModel.onBackRequested()
        viewModel.onBackRequested()
        viewModel.requestDelete()
        advanceUntilIdle()
        assertThat(events).isEmpty()
        assertThat(viewModel.uiState.value.showDeleteConfirm).isFalse()

        gate.complete(SkillStatesUpdateResult.Success(mapOf("sk-1" to false)))
        advanceUntilIdle()
        assertThat(events).containsExactly(SkillDetailEvent.Exit)
        collector.cancel()
    }

    @Test
    fun `back during an on-off save that fails stays to show the failure`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillStatesUpdateResult>()
        coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }

        viewModel.toggleActive()
        advanceUntilIdle()
        viewModel.onBackRequested()
        gate.complete(SkillStatesUpdateResult.Error("nope"))
        advanceUntilIdle()
        assertThat(events).containsExactly(SkillDetailEvent.ActionFailed(SkillDetailAction.TOGGLE_ACTIVE, "nope"))

        viewModel.onBackRequested()
        advanceUntilIdle()
        assertThat(events.last()).isEqualTo(SkillDetailEvent.Exit)
        collector.cancel()
    }

    @Test
    fun `back leaves once, and a delete can't start after it`() = runTest(testDispatcher) {
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }

        viewModel.onBackRequested()
        viewModel.onBackRequested()
        viewModel.requestDelete()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillDetailEvent.Exit)
        assertThat(viewModel.uiState.value.showDeleteConfirm).isFalse()
        collector.cancel()
    }

    // --- gating for owner / shared viewer / shared editor / admin ---

    private val all = SkillAccess(canEdit = true, canDelete = true, canEditFiles = true, canShare = true)

    @Test
    fun `nothing is granted until the viewer and the bits have answered`() = runTest(testDispatcher) {
        val userGate = CompletableDeferred<Result<User>>()
        coEvery { userRepository.getUser() } coAnswers { userGate.await() }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.access).isEqualTo(SkillAccess.NONE)

        userGate.complete(Result.Success(user()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.access).isEqualTo(all)
    }

    @Test
    fun `the owner gets every action`() = runTest(testDispatcher) {
        assertThat(loaded().uiState.value.access).isEqualTo(all)
    }

    @Test
    fun `a shared viewer with role CREATE gets no action`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        coEvery { repo.getSkillEffectivePermissions() } returns Result.Success(mapOf("sk-1" to PermissionBits.VIEW))
        val state = loaded().uiState.value
        assertThat(state.access).isEqualTo(SkillAccess.NONE)
        assertThat(state.canEdit).isFalse()
        assertThat(state.canDelete).isFalse()
    }

    @Test
    fun `a shared editor edits but cannot delete or share`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        coEvery { repo.getSkillEffectivePermissions() } returns
            Result.Success(mapOf("sk-1" to (PermissionBits.VIEW or PermissionBits.EDIT)))
        assertThat(loaded().uiState.value.access).isEqualTo(
            SkillAccess(canEdit = true, canDelete = false, canEditFiles = true, canShare = false),
        )
    }

    @Test
    fun `an admin gets every action on someone else's skill`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        coEvery { userRepository.getUser() } returns Result.Success(user(id = "65f0c0ffee00000000001111", role = "ADMIN"))
        assertThat(loaded().uiState.value.access).isEqualTo(all)
    }

    @Test
    fun `an owner whose role lost CREATE keeps files but loses edit and delete`() = runTest(testDispatcher) {
        every { roleRepository.userPermissions } returns MutableStateFlow(
            UserRolePermissions(name = "USER", permissions = mapOf("SKILLS" to mapOf("USE" to true, "SHARE" to true))),
        )
        assertThat(loaded().uiState.value.access).isEqualTo(
            SkillAccess(canEdit = false, canDelete = false, canEditFiles = true, canShare = true),
        )
    }

    @Test
    fun `a deployment skill offers nothing, even to an admin`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(source = "deployment"))
        coEvery { userRepository.getUser() } returns Result.Success(user(role = "ADMIN"))
        assertThat(loaded().uiState.value.access).isEqualTo(SkillAccess.NONE)
    }

    // --- header inputs ---

    @Test
    fun `the viewer owns a skill they wrote, and not one someone else did`() = runTest(testDispatcher) {
        assertThat(loaded().uiState.value.isOwnedByViewer).isTrue()

        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        assertThat(loaded().uiState.value.isOwnedByViewer).isFalse()
    }

    @Test
    fun `ownership is unknown until the viewer loads`() = runTest(testDispatcher) {
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        assertThat(loaded().uiState.value.isOwnedByViewer).isFalse()
    }

    @Test
    fun `categories load once per screen for the header's label`() = runTest(testDispatcher) {
        val categories = listOf(Category(label = "Writing", value = "write"))
        coEvery { configRepository.getCategories() } returns Result.Success(categories)
        val viewModel = loaded()
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.categories).isEqualTo(categories)
        coVerify(exactly = 1) { configRepository.getCategories() }
    }

    // --- warnings handed back from the editor ---

    @Test
    fun `warnings posted for this skill are shown once on the next load`() = runTest(testDispatcher) {
        val viewModel = loaded()
        saveNotices.post("sk-1", listOf("Description is short; the model may not pick this skill."))
        saveNotices.post("sk-other", listOf("not for this page"))

        viewModel.load()
        viewModel.load()
        advanceUntilIdle()
        // Collected only now: the page is recomposed on return, after the event was sent.
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()

        assertThat(events).containsExactly(
            SkillDetailEvent.SaveWarnings(listOf("Description is short; the model may not pick this skill.")),
        )
        assertThat(saveNotices.take("sk-other")).containsExactly("not for this page")
        collector.cancel()
    }

    // --- a leave waiting on the on/off POST holds the page ---

    @Test
    fun `a back waiting on the on-off save holds the page until it leaves, and lets go if the save fails`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<SkillStatesUpdateResult>()
            coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
            val viewModel = loaded()
            assertThat(viewModel.uiState.value.isHeld).isFalse()

            viewModel.toggleActive()
            advanceUntilIdle()
            viewModel.onBackRequested()
            assertThat(viewModel.uiState.value.isLeaving).isTrue()
            assertThat(viewModel.uiState.value.isHeld).isTrue()

            gate.complete(SkillStatesUpdateResult.Error("nope"))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.isHeld).isFalse()
        }

    @Test
    fun `a leave that lands keeps the page held until it has gone`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<SkillStatesUpdateResult>()
        coEvery { repo.updateSkillStates(any()) } coAnswers { gate.await() }
        val viewModel = loaded()
        viewModel.toggleActive()
        advanceUntilIdle()
        viewModel.onBackRequested()
        gate.complete(SkillStatesUpdateResult.Success(mapOf("sk-1" to false)))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isHeld).isTrue()
    }

    // --- nothing reaches the skill while it is being deleted ---

    @Test
    fun `the on-off toggle is refused while a delete is in flight`() = runTest(testDispatcher) {
        val deleting = CompletableDeferred<Result<DeleteSkillResponse>>()
        coEvery { repo.deleteSkill("sk-1") } coAnswers { deleting.await() }
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.activeStateLoaded).isTrue()

        viewModel.requestDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()
        viewModel.toggleActive()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.updateSkillStates(any()) }
        deleting.complete(Result.Error(message = "kept"))
        advanceUntilIdle()
    }

    // --- a failed reload of a skill on screen is said ---

    @Test
    fun `a failed reload over a shown skill is reported, and a reload that lands drops it`() = runTest(testDispatcher) {
        val viewModel = loaded()
        coEvery { repo.getSkill("sk-1") } returns Result.Error(message = "offline")
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skill).isNotNull()

        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()
        assertThat(events).containsExactly(SkillDetailEvent.ReloadFailed(SkillError(SkillErrorKind.LOAD_SKILL, "offline")))
        collector.cancel()

        // Queued while the page was covered, then answered by a reload that landed: not shown.
        viewModel.load()
        advanceUntilIdle()
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill())
        viewModel.load()
        advanceUntilIdle()
        val later = mutableListOf<SkillDetailEvent>()
        val second = launch { viewModel.events.collect { later += it } }
        advanceUntilIdle()
        assertThat(later.filterIsInstance<SkillDetailEvent.ReloadFailed>()).isEmpty()
        second.cancel()
    }

    @Test
    fun `a failed first load shows the error on the page, not as an event`() = runTest(testDispatcher) {
        coEvery { repo.getSkill("sk-1") } returns Result.Error(message = "offline")
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNotNull()
        assertThat(events.filterIsInstance<SkillDetailEvent.ReloadFailed>()).isEmpty()
        collector.cancel()
    }

    // --- overlapping loads ---

    /** `GET /api/skills/:id` at [version], as a reload after an edit would see it. */
    private fun skillAt(version: Int) = skill().copy(version = version, body = "body v$version")

    @Test
    fun `an older skill fetch that answers last can't put back the content from before an edit`() =
        runTest(testDispatcher) {
            val viewModel = loaded()
            val older = CompletableDeferred<Result<Skill>>()
            coEvery { repo.getSkill("sk-1") } coAnswers { older.await() } andThen Result.Success(skillAt(4))

            viewModel.load() // A resume: GET A, still on its way.
            runCurrent()
            viewModel.load() // Back from the editor's save: GET B answers v4.
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.skill?.version).isEqualTo(4)

            older.complete(Result.Success(skillAt(3)))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.skill?.version).isEqualTo(4)
            assertThat(viewModel.uiState.value.skill?.body).isEqualTo("body v4")
        }

    @Test
    fun `an older skill fetch that fails after a newer one landed reports nothing`() = runTest(testDispatcher) {
        val viewModel = loaded()
        val events = mutableListOf<SkillDetailEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        val older = CompletableDeferred<Result<Skill>>()
        coEvery { repo.getSkill("sk-1") } coAnswers { older.await() } andThen Result.Success(skillAt(4))

        viewModel.load()
        runCurrent()
        viewModel.load()
        advanceUntilIdle()
        older.complete(Result.Error(message = "timeout"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(events.filterIsInstance<SkillDetailEvent.ReloadFailed>()).isEmpty()
        collector.cancel()
    }

    @Test
    fun `an older permission-bits fetch that answers last can't take back newer bits`() = runTest(testDispatcher) {
        // Someone else's skill; this viewer's SHARE and EDIT bits arrive with the newer fetch.
        coEvery { repo.getSkill("sk-1") } returns Result.Success(skill(author = someoneElse))
        val viewModel = loaded()
        assertThat(viewModel.uiState.value.canEdit).isFalse()
        val older = CompletableDeferred<Result<Map<String, Int>>>()
        coEvery { repo.getSkillEffectivePermissions() } coAnswers { older.await() } andThen
            Result.Success(mapOf("sk-1" to (PermissionBits.VIEW or PermissionBits.EDIT)))

        viewModel.load()
        runCurrent()
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEdit).isTrue()

        older.complete(Result.Success(mapOf("sk-1" to PermissionBits.VIEW)))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canEdit).isTrue()
    }
}
