package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerFailure
import com.garfiec.librechat.core.data.datastore.DrawerPinsStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmarks_load_failed
import com.garfiec.librechat.feature.conversations.resources.favorite_update_failed
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The drawer's bookmark surfaces: one gate (role and `interface.bookmarks`), and the checklist. */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerBookmarksTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userPermissions = MutableStateFlow<UserRolePermissions?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val tagRepository = mockk<TagRepository>(relaxed = true) {
        every { observeTags() } returns MutableStateFlow(listOf(ConversationTag(tag = "work")))
    }
    private val projectRepository = mockk<ProjectRepository>(relaxed = true) {
        every { revision } returns MutableStateFlow(0L)
    }
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user")))

    private fun viewModel() = DrawerViewModel(
        // The drawer's state only flows once every input has emitted.
        conversationRepository = mockk<ConversationRepository>(relaxed = true) {
            every { observeConversations(any()) } returns MutableStateFlow(Result.Success(emptyList()))
        },
        roleRepository = mockk<RoleRepository>(relaxed = true) {
            every { userPermissions } returns this@DrawerBookmarksTest.userPermissions
        },
        tagRepository = tagRepository,
        projectRepository = projectRepository,
        configRepository = mockk<ConfigRepository>(relaxed = true) {
            every { detectedBackendVersion } returns MutableStateFlow(null)
            every { startupConfig } returns this@DrawerBookmarksTest.startupConfig
            every { endpointConfigs } returns MutableStateFlow(emptyMap())
            every { serverUrl } returns flowOf("https://srv")
        },
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        activeAccountProvider = accounts,
        preferences = DrawerPreferences(
            settings = mockk<SettingsDataStore>(relaxed = true) {
                every { drawerLibraryTab } returns flowOf(null)
            },
            pins = mockk<DrawerPinsStore> { every { pinned } returns MutableStateFlow(emptyList()) },
        ),
        chatRepository = mockk(relaxed = true),
        search = mockk<DrawerSearchSources>(relaxed = true) {
            every { messageSearch(any(), any()) } returns flowOf(DrawerMessageSearch.Idle)
        },
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // The drawer runs polling loops in viewModelScope, so the scope is cancelled at the end.
    private fun drawerTest(body: suspend TestScope.(DrawerViewModel) -> Unit) = runTest(testDispatcher) {
        val vm = viewModel()
        val keepUp = backgroundScope.launch { vm.drawerUiState.collect {} }
        try {
            body(vm)
        } finally {
            keepUp.cancel()
            vm.viewModelScope.cancel()
        }
    }

    /** Past the conversation list's debounce, so the drawer state has caught up. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }

    private fun role(bookmarksUse: Boolean) =
        UserRolePermissions(name = "USER", permissions = mapOf("BOOKMARKS" to mapOf("USE" to bookmarksUse)))

    @Test
    fun `bookmarks show while the role is unknown and no interface flag says otherwise`() = drawerTest { vm ->
        settle()
        assertThat(vm.drawerUiState.value.bookmarksEnabled).isTrue()
    }

    @Test
    fun `interface bookmarks false hides every bookmark surface, whatever the role`() = drawerTest { vm ->
        userPermissions.value = role(bookmarksUse = true)
        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(bookmarks = false))
        settle()
        assertThat(vm.drawerUiState.value.bookmarksEnabled).isFalse()
    }

    @Test
    fun `a role without BOOKMARKS USE hides every bookmark surface`() = drawerTest { vm ->
        userPermissions.value = role(bookmarksUse = false)
        settle()
        assertThat(vm.drawerUiState.value.bookmarksEnabled).isFalse()
    }

    @Test
    fun `Bookmarks opens the checklist on the chat's server tags`() = drawerTest { vm ->
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(listOf("work"))
        settle()

        vm.openBookmarks("c1")
        settle()

        assertThat(vm.bookmarkPicker.state.value.conversationId).isEqualTo("c1")
        assertThat(vm.bookmarkPicker.state.value.attached).containsExactly("work")
    }

    @Test
    fun `the checklist doesn't open while bookmarks are off, and closes when they turn off`() = drawerTest { vm ->
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(bookmarks = false))
        settle()
        vm.openBookmarks("c1")
        settle()
        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
        coVerify(exactly = 0) { tagRepository.getConversationTags(any()) }

        startupConfig.value = StartupConfig()
        settle()
        vm.openBookmarks("c1")
        settle()
        assertThat(vm.bookmarkPicker.state.value.isOpen).isTrue()

        userPermissions.value = role(bookmarksUse = false)
        settle()
        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
    }

    @Test
    fun `failures are told in the user's language, a tick's inside the open sheet`() = drawerTest { vm ->
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { tagRepository.getConversationTags("c2") } returns Result.Error(message = "offline")
        coEvery { tagRepository.setConversationTag("c1", "work", true) } returns Result.Error(message = "boom")
        coEvery { tagRepository.setConversationTag("c1", SAVED_TAG, true) } returns Result.Error(message = "boom")
        val events = mutableListOf<ConversationListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        settle()

        vm.openBookmarks("c1")
        settle()
        vm.bookmarkPicker.toggle("work")
        settle()
        // A toast or snackbar would sit behind the sheet: the sheet says it.
        assertThat(vm.bookmarkPicker.state.value.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("work"))
        vm.bookmarkPicker.close()
        vm.setFavorite("c1", favorite = true)
        settle()
        vm.openBookmarks("c2")
        settle()

        assertThat(events).containsExactly(
            ConversationListEvent.ShowErrorRes(Res.string.favorite_update_failed),
            ConversationListEvent.ShowErrorRes(Res.string.bookmarks_load_failed),
        ).inOrder()
    }

    @Test
    fun `an account switch closes the checklist, so a late tick can't write to the new account`() = drawerTest { vm ->
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        settle()
        vm.openBookmarks("c1")
        settle()
        assertThat(vm.bookmarkPicker.state.value.isOpen).isTrue()

        accounts.set(AccountId("srv:other"))
        settle()

        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
        vm.bookmarkPicker.toggle("work")
        settle()
        coVerify(exactly = 0) { tagRepository.setConversationTag(any(), any(), any()) }
    }

    @Test
    fun `an account switch drops ticks and stars still out for the old account, unreported`() = drawerTest { vm ->
        val tick = CompletableDeferred<Result<List<String>>>()
        val star = CompletableDeferred<Result<List<String>>>()
        coEvery { tagRepository.getConversationTags("c1") } returns Result.Success(emptyList())
        coEvery { tagRepository.setConversationTag("c1", "work", true) } coAnswers { tick.await() }
        coEvery { tagRepository.setConversationTag("c2", SAVED_TAG, true) } coAnswers { star.await() }
        val events = mutableListOf<ConversationListEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        settle()
        vm.openBookmarks("c1")
        settle()
        vm.bookmarkPicker.toggle("work")
        vm.bookmarkPicker.close()
        vm.setFavorite("c2", favorite = true)
        settle()

        accounts.set(AccountId("srv:other"))
        settle()
        tick.complete(Result.Error(message = "boom"))
        star.complete(Result.Error(message = "boom"))
        settle()

        assertThat(events).isEmpty()
    }

    @Test
    fun `pull to refresh refreshes the tags only while bookmarks are on`() = drawerTest { vm ->
        settle()
        vm.refreshConversations()
        settle()
        coVerify(exactly = 1) { tagRepository.refreshTags() }

        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(bookmarks = false))
        settle()
        vm.refreshConversations()
        settle()
        userPermissions.value = role(bookmarksUse = false)
        startupConfig.value = StartupConfig()
        settle()
        vm.refreshConversations()
        settle()

        coVerify(exactly = 1) { tagRepository.refreshTags() }
    }
}
