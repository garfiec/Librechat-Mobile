package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserPersonalization
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.network.api.MemoriesApi
import com.garfiec.librechat.core.network.api.UserApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The memory opt-out is one flow every screen reads, so it must describe the ACTIVE account: a
 * value left over from the outgoing account would show (and, in chat, obey) someone else's switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryRepositoryPreferenceTest {

    private val memoriesApi = mockk<MemoriesApi>()
    private val userApi = mockk<UserApi>()
    private val configRepository = mockk<ConfigRepository>().also {
        every { it.detectedBackend } returns MutableStateFlow(null)
    }
    private val accounts = InMemoryActiveAccountProvider().apply { set(AccountId("srv:user-a")) }

    private fun TestScope.repository(): MemoryRepository = MemoryRepositoryImpl(
        memoriesApi = memoriesApi,
        userApi = userApi,
        configRepository = configRepository,
        activeAccountProvider = accounts,
        applicationScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    private fun user(memories: Boolean?) = User(
        email = "a@example.com",
        personalization = memories?.let { UserPersonalization(memories = it) },
    )

    @Test
    fun `the preference is unknown until it is read`() = runTest {
        val repo = repository()
        advanceUntilIdle()

        assertThat(repo.memoriesEnabled.value).isNull()
    }

    @Test
    fun `refresh seeds the preference from the profile`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = repository()

        val result = repo.refreshMemoriesPreference()

        assertThat(result).isEqualTo(Result.Success(false))
        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    @Test
    fun `a profile without a personalization block reads as on`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = null)
        val repo = repository()

        repo.refreshMemoriesPreference()

        assertThat(repo.memoriesEnabled.value).isTrue()
    }

    @Test
    fun `a failed read leaves the preference unknown`() = runTest {
        coEvery { userApi.getUser() } throws IllegalStateException("offline")
        val repo = repository()

        val result = repo.refreshMemoriesPreference()

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(repo.memoriesEnabled.value).isNull()
    }

    @Test
    fun `a successful PATCH publishes the stored preference`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = true)
        coEvery { memoriesApi.updatePreferences(any()) } returns MemoryPreferences(enabled = false)
        val repo = repository()
        repo.refreshMemoriesPreference()

        repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))

        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    @Test
    fun `a failed PATCH keeps the last known preference`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = true)
        coEvery { memoriesApi.updatePreferences(any()) } throws IllegalStateException("offline")
        val repo = repository()
        repo.refreshMemoriesPreference()

        repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))

        assertThat(repo.memoriesEnabled.value).isTrue()
    }

    @Test
    fun `an account switch resets the preference to unknown`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = repository()
        advanceUntilIdle()
        repo.refreshMemoriesPreference()
        assertThat(repo.memoriesEnabled.value).isFalse()

        accounts.set(AccountId("srv:user-b"))
        advanceUntilIdle()

        assertThat(repo.memoriesEnabled.value).isNull()
    }

    @Test
    fun `a logout resets the preference to unknown`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = repository()
        advanceUntilIdle()
        repo.refreshMemoriesPreference()

        accounts.clear()
        advanceUntilIdle()

        assertThat(repo.memoriesEnabled.value).isNull()
    }

    /** The first resolution at cold start is not a switch: a preference already read stays. */
    @Test
    fun `the first account resolution keeps the preference`() = runTest {
        val cold = InMemoryActiveAccountProvider()
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = MemoryRepositoryImpl(
            memoriesApi = memoriesApi,
            userApi = userApi,
            configRepository = configRepository,
            activeAccountProvider = cold,
            applicationScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        repo.refreshMemoriesPreference()

        cold.set(AccountId("srv:user-a"))
        advanceUntilIdle()

        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    /** B's own read beat the reset: B's answer stays rather than being wiped back to unknown. */
    @Test
    fun `the incoming account's value survives a late reset`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = repository()
        advanceUntilIdle()
        repo.refreshMemoriesPreference()

        accounts.set(AccountId("srv:user-b"))
        coEvery { userApi.getUser() } returns user(memories = true)
        repo.refreshMemoriesPreference()
        advanceUntilIdle()

        assertThat(repo.memoriesEnabled.value).isTrue()
    }

    /** A's read lands after the switch: B must not show A's opt-out. */
    @Test
    fun `a read that lands after a switch is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { userApi.getUser() } coAnswers {
            gate.await()
            user(memories = false)
        }
        val repo = repository()
        advanceUntilIdle()

        val refresh = async { repo.refreshMemoriesPreference() }
        runCurrent()
        accounts.set(AccountId("srv:user-b"))
        advanceUntilIdle()
        gate.complete(Unit)
        refresh.await()

        assertThat(repo.memoriesEnabled.value).isNull()
    }

    /** The GET was answered before the PATCH was applied, so its value is older than the PATCH's. */
    @Test
    fun `a read in flight when a PATCH starts does not undo it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { userApi.getUser() } coAnswers {
            gate.await()
            user(memories = true)
        }
        coEvery { memoriesApi.updatePreferences(any()) } returns MemoryPreferences(enabled = false)
        val repo = repository()
        advanceUntilIdle()

        val refresh = async { repo.refreshMemoriesPreference() }
        runCurrent()
        repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))
        assertThat(repo.memoriesEnabled.value).isFalse()
        gate.complete(Unit)
        refresh.await()

        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    /** Started while the PATCH ran, so the server may have answered it before applying the write. */
    @Test
    fun `a read that overlaps a PATCH in flight does not undo it`() = runTest {
        val patchGate = CompletableDeferred<Unit>()
        val readGate = CompletableDeferred<Unit>()
        coEvery { memoriesApi.updatePreferences(any()) } coAnswers {
            patchGate.await()
            MemoryPreferences(enabled = false)
        }
        coEvery { userApi.getUser() } coAnswers {
            readGate.await()
            user(memories = true)
        }
        val repo = repository()
        advanceUntilIdle()

        val patch = async { repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false)) }
        runCurrent()
        val refresh = async { repo.refreshMemoriesPreference() }
        runCurrent()
        patchGate.complete(Unit)
        patch.await()
        readGate.complete(Unit)
        refresh.await()

        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    @Test
    fun `a read that starts after a PATCH has finished publishes`() = runTest {
        coEvery { memoriesApi.updatePreferences(any()) } returns MemoryPreferences(enabled = false)
        coEvery { userApi.getUser() } returns user(memories = true)
        val repo = repository()
        advanceUntilIdle()

        repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))
        repo.refreshMemoriesPreference()

        assertThat(repo.memoriesEnabled.value).isTrue()
    }

    @Test
    fun `a PATCH that lands after a switch is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { memoriesApi.updatePreferences(any()) } coAnswers {
            gate.await()
            MemoryPreferences(enabled = false)
        }
        val repo = repository()
        advanceUntilIdle()

        val patch = async { repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false)) }
        runCurrent()
        accounts.set(AccountId("srv:user-b"))
        advanceUntilIdle()
        gate.complete(Unit)
        patch.await()

        assertThat(repo.memoriesEnabled.value).isNull()
    }

    /**
     * The user leaves the Memories page while the PATCH is in flight. The server still applies it,
     * so the shared flow open chats follow must still learn the stored value.
     */
    @Test
    fun `a PATCH whose caller is cancelled still publishes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { userApi.getUser() } returns user(memories = true)
        coEvery { memoriesApi.updatePreferences(any()) } coAnswers {
            gate.await()
            MemoryPreferences(enabled = false)
        }
        val repo = repository()
        repo.refreshMemoriesPreference()

        val patch = async { repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false)) }
        runCurrent()
        patch.cancel()
        gate.complete(Unit)

        // The call hops to the IO dispatcher, so wait for the publish in real time; runTest's own
        // timeout bounds it if it never comes.
        assertThat(repo.memoriesEnabled.first { it == false }).isFalse()
        assertThat(patch.isCancelled).isTrue()
    }

    /** A chat's own profile read: newer than a value Settings cached earlier in the process. */
    @Test
    fun `a caller's profile read replaces the cached preference`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = true)
        val repo = repository()
        repo.refreshMemoriesPreference()
        val profile = user(memories = false)

        val result = repo.fetchUserPublishingPreference { Result.Success(profile) }

        assertThat(result).isEqualTo(Result.Success(profile))
        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    @Test
    fun `a caller's failed profile read keeps the cached preference`() = runTest {
        coEvery { userApi.getUser() } returns user(memories = false)
        val repo = repository()
        repo.refreshMemoriesPreference()

        val result = repo.fetchUserPublishingPreference { Result.Error(IllegalStateException("offline")) }

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(repo.memoriesEnabled.value).isFalse()
    }

    @Test
    fun `a caller's profile read in flight when a PATCH starts does not undo it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { memoriesApi.updatePreferences(any()) } returns MemoryPreferences(enabled = false)
        val repo = repository()
        advanceUntilIdle()

        val read = async {
            repo.fetchUserPublishingPreference {
                gate.await()
                Result.Success(user(memories = true))
            }
        }
        runCurrent()
        repo.updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))
        gate.complete(Unit)
        read.await()

        assertThat(repo.memoriesEnabled.value).isFalse()
    }
}
