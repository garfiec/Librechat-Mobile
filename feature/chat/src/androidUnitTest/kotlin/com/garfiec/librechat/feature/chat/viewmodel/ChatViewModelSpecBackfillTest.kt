package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A conversation cached before the spec column has no spec, and an enforced server refuses its
 * sends until the client asks the server for it. The ask must not depend on the seeder having run.
 *
 * The record of answered conversations is process-wide, so each test opens its own conversation id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSpecBackfillTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val conversationRepository get() = fixture.conversationRepository
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)

    private val enforced = StartupConfig(
        modelSpecs = ModelSpecs(
            list = listOf(ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))),
            enforce = true,
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { fixture.configRepository.startupConfig } returns startupConfig
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cached(id: String) = Conversation(conversationId = id, endpoint = "openAI", model = "gpt-4.1")

    private fun open(id: String, account: String = "srv:user-1") = fixture.build(
        defaultDispatcher = testDispatcher,
        initialConversationId = id,
        activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId(account))),
    )

    @Test
    fun aSpecLessConversationIsAskedForOnceUnderEnforce() = runTest(testDispatcher) {
        startupConfig.value = enforced
        coEvery { conversationRepository.getConversation("bf-once", any()) } returns Result.Success(cached("bf-once"))
        coEvery { conversationRepository.refreshConversation("bf-once", any()) } returns
            Result.Success(cached("bf-once").copy(spec = "fast"))

        open("bf-once")
        advanceUntilIdle()
        open("bf-once")
        advanceUntilIdle()

        coVerify(exactly = 1) { conversationRepository.refreshConversation("bf-once", any()) }
    }

    @Test
    fun withoutTheConfigNothingIsAskedAndTheNextOpenStillAsks() = runTest(testDispatcher) {
        coEvery { conversationRepository.getConversation("bf-later", any()) } returns Result.Success(cached("bf-later"))
        coEvery { conversationRepository.refreshConversation("bf-later", any()) } returns
            Result.Success(cached("bf-later").copy(spec = "fast"))

        open("bf-later")
        advanceUntilIdle()
        coVerify(exactly = 0) { conversationRepository.refreshConversation("bf-later", any()) }

        startupConfig.value = enforced
        open("bf-later")
        advanceUntilIdle()
        coVerify(exactly = 1) { conversationRepository.refreshConversation("bf-later", any()) }
    }

    @Test
    fun aFailedAskIsRetriedOnTheNextOpen() = runTest(testDispatcher) {
        startupConfig.value = enforced
        coEvery { conversationRepository.getConversation("bf-retry", any()) } returns Result.Success(cached("bf-retry"))
        coEvery { conversationRepository.refreshConversation("bf-retry", any()) } returns Result.Error(message = "offline")

        open("bf-retry")
        advanceUntilIdle()
        open("bf-retry")
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.refreshConversation("bf-retry", any()) }
    }

    @Test
    fun anotherAccountsConversationWithTheSameIdIsAskedForAgain() = runTest(testDispatcher) {
        startupConfig.value = enforced
        coEvery { conversationRepository.getConversation("bf-shared", any()) } returns Result.Success(cached("bf-shared"))
        coEvery { conversationRepository.refreshConversation("bf-shared", any()) } returns
            Result.Success(cached("bf-shared"))

        open("bf-shared", account = "srv-a:user-1")
        advanceUntilIdle()
        open("bf-shared", account = "srv-b:user-1")
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.refreshConversation("bf-shared", any()) }
    }
}
