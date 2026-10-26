package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.ui.components.ModelParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

/**
 * A reopened conversation runs on the parameters it was last sent with, as on web. The list route
 * sends none, so a row it cached is asked for once per process. The record is process-wide, so
 * each test opens its own conversation id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelConversationParametersTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val conversationRepository get() = fixture.conversationRepository
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { fixture.configRepository.startupConfig } returns startupConfig
        // The seeder combines last-used; a relaxed mock's flow never emits.
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun listed(id: String) = Conversation(conversationId = id, endpoint = "openAI", model = "gpt-4.1")

    private fun open(id: String) = fixture.build(
        defaultDispatcher = testDispatcher,
        initialConversationId = id,
        activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-1"))),
    )

    @Test
    fun storedParametersAreRestoredWithoutAsking() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("cp-cached", any()) } returns
            Result.Success(listed("cp-cached").copy(temperature = 0.2, reasoningEffort = "high"))

        val vm = open("cp-cached")
        advanceUntilIdle()

        assertEquals(0.2f, vm.uiState.value.modelParameters.temperature)
        assertEquals("high", vm.uiState.value.modelParameters.dynamicValues["reasoning_effort"])
        coVerify(exactly = 0) { conversationRepository.refreshConversation("cp-cached", any()) }
    }

    @Test
    fun aListedRowIsAskedForItsParametersOnce() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("cp-listed", any()) } returns Result.Success(listed("cp-listed"))
        coEvery { conversationRepository.refreshConversation("cp-listed", any()) } returns
            Result.Success(listed("cp-listed").copy(maxContextTokens = 64000))

        val vm = open("cp-listed")
        advanceUntilIdle()
        open("cp-listed")
        advanceUntilIdle()

        assertEquals(64000, vm.uiState.value.modelParameters.maxContextTokens)
        coVerify(exactly = 1) { conversationRepository.refreshConversation("cp-listed", any()) }
    }

    @Test
    fun storedParametersWinOverTheSpecsPreset() = runTest(testDispatcher) {
        startupConfig.value = StartupConfig(
            modelSpecs = ModelSpecs(
                list = listOf(
                    ModelSpec(name = "deep", preset = Preset(endpoint = "openAI", model = "gpt-4.1", temperature = 0.9)),
                ),
            ),
        )
        coEvery { conversationRepository.getConversation("cp-spec", any()) } returns
            Result.Success(listed("cp-spec").copy(spec = "deep", temperature = 0.1))

        val vm = open("cp-spec")
        advanceUntilIdle()

        assertEquals("deep", vm.uiState.value.selectedSpec)
        assertEquals(0.1f, vm.uiState.value.modelParameters.temperature)
    }

    @Test
    fun underEnforceAReopenedSpecChatShowsThePresetTheServerRuns() = runTest(testDispatcher) {
        startupConfig.value = StartupConfig(
            modelSpecs = ModelSpecs(
                enforce = true,
                list = listOf(
                    ModelSpec(name = "deep", preset = Preset(endpoint = "openAI", model = "gpt-4.1", temperature = 0.9)),
                ),
            ),
        )
        coEvery { conversationRepository.getConversation("cp-enforce", any()) } returns
            Result.Success(listed("cp-enforce").copy(spec = "deep", temperature = 0.1))

        val vm = open("cp-enforce")
        advanceUntilIdle()

        assertEquals("deep", vm.uiState.value.selectedSpec)
        assertEquals(0.9f, vm.uiState.value.modelParameters.temperature)
    }

    /** Parameters cached at version 1 of the chat, on a row the list has since moved to version 2. */
    private fun stale(id: String) = listed(id).copy(
        temperature = 0.2,
        parametersAsOf = Instant.fromEpochMilliseconds(1_000),
        updatedAt = Instant.fromEpochMilliseconds(2_000),
    )

    @Test
    fun aChatChangedElsewhereShowsItsCacheThenTheServersParameters() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { conversationRepository.getConversation("cp-stale", any()) } returns Result.Success(stale("cp-stale"))
        coEvery { conversationRepository.refreshConversation("cp-stale", any()) } coAnswers {
            gate.await()
            Result.Success(listed("cp-stale").copy(temperature = 0.7))
        }

        val vm = open("cp-stale")
        advanceUntilIdle()
        assertEquals(0.2f, vm.uiState.value.modelParameters.temperature)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(0.7f, vm.uiState.value.modelParameters.temperature)
    }

    @Test
    fun anOfflineOpenOfAChangedChatKeepsItsCache() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("cp-offline", any()) } returns Result.Success(stale("cp-offline"))
        coEvery { conversationRepository.refreshConversation("cp-offline", any()) } returns
            Result.Error(IllegalStateException("offline"))

        val vm = open("cp-offline")
        advanceUntilIdle()

        assertEquals(0.2f, vm.uiState.value.modelParameters.temperature)
    }

    @Test
    fun aTweakMadeWhileTheFetchIsOutStays() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { conversationRepository.getConversation("cp-tweak", any()) } returns Result.Success(stale("cp-tweak"))
        coEvery { conversationRepository.refreshConversation("cp-tweak", any()) } coAnswers {
            gate.await()
            Result.Success(listed("cp-tweak").copy(temperature = 0.7))
        }

        val vm = open("cp-tweak")
        advanceUntilIdle()
        vm.updateModelParameters(vm.uiState.value.modelParameters.copy(temperature = 0.4f))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0.4f, vm.uiState.value.modelParameters.temperature)
    }

    @Test
    fun anUnchangedChatIsNotAskedAgain() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("cp-fresh", any()) } returns
            Result.Success(stale("cp-fresh").copy(updatedAt = Instant.fromEpochMilliseconds(1_000)))

        open("cp-fresh")
        advanceUntilIdle()

        coVerify(exactly = 0) { conversationRepository.refreshConversation("cp-fresh", any()) }
    }

    @Test
    fun aHandedOffNewChatKeepsWhatItsFirstTurnWasSentWith() = runTest(testDispatcher) {
        val sent = ModelParameters.DEFAULT.copy(temperature = 0.3f)
        fixture.selectionHandoff.put("cp-handoff", ModelChoice.Raw("openAI", "gpt-4.1"), modelParameters = sent)
        coEvery { conversationRepository.getConversation("cp-handoff", any()) } returns
            Result.Success(listed("cp-handoff").copy(temperature = 0.8))

        val vm = open("cp-handoff")
        advanceUntilIdle()

        assertEquals(0.3f, vm.uiState.value.modelParameters.temperature)
        coVerify(exactly = 0) { conversationRepository.refreshConversation("cp-handoff", any()) }
    }

    @Test
    fun aConversationThatStoredNothingOpensOnTheDefaults() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("cp-none", any()) } returns Result.Success(listed("cp-none"))
        coEvery { conversationRepository.refreshConversation("cp-none", any()) } returns Result.Success(listed("cp-none"))

        val vm = open("cp-none")
        advanceUntilIdle()

        assertEquals(ModelParameters.DEFAULT, vm.uiState.value.modelParameters)
        assertNull(vm.uiState.value.selectedSpec)
    }

    @Test
    fun theNextNewChatStartsFromTheCarriedSpecsPresetNotTheLastTweak() = runTest(testDispatcher) {
        every { fixture.configRepository.availableModels } returns MutableStateFlow(mapOf("openAI" to listOf("gpt-4.1")))
        every { fixture.configRepository.endpointConfigs } returns MutableStateFlow(mapOf("openAI" to EndpointConfig()))
        startupConfig.value = StartupConfig(
            modelSpecs = ModelSpecs(
                list = listOf(
                    ModelSpec(name = "deep", preset = Preset(endpoint = "openAI", model = "gpt-4.1", temperature = 0.9)),
                ),
            ),
            interfaceConfig = InterfaceConfig(modelSelect = false),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher)
        advanceUntilIdle()
        assertEquals("deep", vm.uiState.value.selectedSpec)
        assertEquals(0.9f, vm.uiState.value.modelParameters.temperature)

        vm.updateModelParameters(vm.uiState.value.modelParameters.copy(temperature = 0.1f))
        vm.onPendingNavigationHandled()
        advanceUntilIdle()

        assertEquals("deep", vm.uiState.value.selectedSpec)
        assertEquals(0.9f, vm.uiState.value.modelParameters.temperature)
    }
}
