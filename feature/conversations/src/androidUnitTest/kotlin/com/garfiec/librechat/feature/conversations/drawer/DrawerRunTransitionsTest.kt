package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.DrawerPinsStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.Conversation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

/**
 * A chat that starts or stops running while the drawer is up is re-read off the server, so its
 * cached row takes the reply's unseen stamp (Finished, the dot) and an archive made elsewhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawerRunTransitionsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val account = AccountId("srv:user")
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(account))

    private fun convo(id: String) = Conversation(conversationId = id, title = id, updatedAt = Instant.parse("2026-02-19T10:00:00Z"))

    /** Room holds the open chat A and the background chats B and D; U is filed in a project. */
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true) {
        every { observeConversations(any()) } returns MutableStateFlow(Result.Success(listOf(convo("A"), convo("B"), convo("D"))))
        coEvery { refreshConversation(any(), any()) } returns Result.Error(message = "unused")
        coEvery { fetchConversationUncached(any()) } returns Result.Error(message = "unused")
    }
    private val chatRepository = mockk<ChatRepository>(relaxed = true)

    private fun viewModel() = DrawerViewModel(
        conversationRepository = conversationRepository,
        roleRepository = mockk<RoleRepository>(relaxed = true) { every { userPermissions } returns MutableStateFlow(null) },
        tagRepository = mockk(relaxed = true),
        projectRepository = mockk<ProjectRepository>(relaxed = true) { every { revision } returns MutableStateFlow(0L) },
        configRepository = mockk<ConfigRepository>(relaxed = true) {
            every { detectedBackendVersion } returns MutableStateFlow(null)
            every { startupConfig } returns MutableStateFlow(null)
            every { endpointConfigs } returns MutableStateFlow(emptyMap())
            every { serverUrl } returns flowOf("https://srv")
        },
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        activeAccountProvider = accounts,
        preferences = DrawerPreferences(
            settings = mockk<SettingsDataStore>(relaxed = true) { every { drawerLibraryTab } returns flowOf(null) },
            pins = mockk<DrawerPinsStore> { every { pinned } returns MutableStateFlow(emptyList()) },
        ),
        chatRepository = chatRepository,
        pullRequestRepository = mockk<PullRequestRepository> {
            every { entries } returns flowOf(emptyMap())
            every { revision } returns MutableStateFlow(0L)
        },
        search = mockk<DrawerSearchSources>(relaxed = true) {
            every { messageSearch(any(), any()) } returns flowOf(DrawerMessageSearch.Idle)
        },
        connectivityObserver = mockk<ConnectivityObserver> { every { isConnected } returns flowOf(true) },
        savedStateHandle = SavedStateHandle(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached chats that start or stop running are re-read, except the open one and unlisted ones`() = runTest(testDispatcher) {
        coEvery { chatRepository.getActiveJobIds() } returnsMany listOf(
            setOf("A", "U"),
            setOf("A", "U", "B"),
            setOf("A"),
            emptySet(),
        ).map { Result.Success(it) }
        val vm = viewModel()
        val keepUp = backgroundScope.launch { vm.drawerUiState.collect {} }
        try {
            vm.setActiveConversation("A")
            runCurrent()
            vm.setDrawerVisible(true)
            runCurrent()
            repeat(4) {
                advanceTimeBy(POLL_MS)
                runCurrent()
            }

            // Once as it started, once as it stopped.
            coVerify(exactly = 2) { conversationRepository.refreshConversation("B", account) }
            coVerify(exactly = 0) { conversationRepository.refreshConversation("A", any()) }
            coVerify(exactly = 0) { conversationRepository.refreshConversation("U", any()) }
            coVerify(exactly = 0) { conversationRepository.refreshConversation("D", any()) }
        } finally {
            keepUp.cancel()
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a failed poll is no information, not every run finishing`() = runTest(testDispatcher) {
        coEvery { chatRepository.getActiveJobIds() } returnsMany listOf(
            Result.Success(setOf("B")),
            Result.Error(message = "offline"),
            Result.Success(setOf("B")),
        )
        val vm = viewModel()
        val keepUp = backgroundScope.launch { vm.drawerUiState.collect {} }
        try {
            vm.setDrawerVisible(true)
            runCurrent()
            repeat(3) {
                advanceTimeBy(POLL_MS)
                runCurrent()
            }

            // Only the start: the failed poll in between read as no change.
            coVerify(exactly = 1) { conversationRepository.refreshConversation("B", account) }
        } finally {
            keepUp.cancel()
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        /** DrawerViewModel's poll interval while anything runs. */
        const val POLL_MS = 5_000L
    }
}
