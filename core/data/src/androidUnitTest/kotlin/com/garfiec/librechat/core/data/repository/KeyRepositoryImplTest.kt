package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.endpoint.KeyInvalidation
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.core.model.response.KeyExpiryResponse
import com.garfiec.librechat.core.network.api.KeysApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class KeyRepositoryImplTest {

    private val keysApi = mockk<KeysApi>(relaxed = true)

    private val sut = KeyRepositoryImpl(keysApi = keysApi)

    @Test
    fun `updateKey returns Success of Unit`() = runTest {
        val request = UpdateKeyRequest(
            name = "openAI",
            value = """{"apiKey":"sk-x","baseURL":""}""",
            expiresAt = "2026-05-01T00:00:00Z",
        )
        coEvery { keysApi.updateKey(request) } returns Unit

        val result = sut.updateKey(request)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { keysApi.updateKey(request) }
    }

    @Test
    fun `updateKey emits keyInvalidations on success`() = runTest {
        val request = UpdateKeyRequest(
            name = "openAI",
            value = """{"apiKey":"sk-x","baseURL":""}""",
            expiresAt = "2026-05-01T00:00:00Z",
        )
        coEvery { keysApi.updateKey(request) } returns Unit

        val emitted = mutableListOf<KeyInvalidation>()
        val collectorJob = launch { sut.keyInvalidations.collect { emitted.add(it) } }
        advanceUntilIdle()

        sut.updateKey(request)
        advanceUntilIdle()

        assertThat(emitted).containsExactly(KeyInvalidation.ByName("openAI"))
        collectorJob.cancel()
    }

    @Test
    fun `deleteKey emits keyInvalidations on success`() = runTest {
        coEvery { keysApi.deleteKey("openAI") } returns Unit

        val emitted = mutableListOf<KeyInvalidation>()
        val collectorJob = launch { sut.keyInvalidations.collect { emitted.add(it) } }
        advanceUntilIdle()

        sut.deleteKey("openAI")
        advanceUntilIdle()

        assertThat(emitted).containsExactly(KeyInvalidation.ByName("openAI"))
        collectorJob.cancel()
    }

    @Test
    fun `deleteAllKeys emits KeyInvalidation All on success`() = runTest {
        coEvery { keysApi.deleteAllKeys() } returns Unit

        val emitted = mutableListOf<KeyInvalidation>()
        val collectorJob = launch { sut.keyInvalidations.collect { emitted.add(it) } }
        advanceUntilIdle()

        sut.deleteAllKeys()
        advanceUntilIdle()

        assertThat(emitted).containsExactly(KeyInvalidation.All)
        collectorJob.cancel()
    }

    @Test
    fun `updateKey does not emit on failure`() = runTest {
        val request = UpdateKeyRequest(name = "openAI", value = "{}", expiresAt = "never")
        coEvery { keysApi.updateKey(request) } throws RuntimeException("boom")

        val emitted = mutableListOf<KeyInvalidation>()
        val collectorJob = launch { sut.keyInvalidations.collect { emitted.add(it) } }
        advanceUntilIdle()

        val result = sut.updateKey(request)
        advanceUntilIdle()

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(emitted).isEmpty()
        collectorJob.cancel()
    }

    @Test
    fun `fetchKeyState maps null wire to Unset`() = runTest {
        coEvery { keysApi.getKeyExpiry("openAI") } returns KeyExpiryResponse(expiresAt = null)

        val result = sut.fetchKeyState("openAI")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(KeyState.Unset)
    }

    @Test
    fun `fetchKeyState maps empty wire to Unset`() = runTest {
        coEvery { keysApi.getKeyExpiry("openAI") } returns KeyExpiryResponse(expiresAt = "")

        val result = sut.fetchKeyState("openAI")

        assertThat((result as Result.Success).data).isEqualTo(KeyState.Unset)
    }

    @Test
    fun `fetchKeyState maps never literal to Set with neverExpires true`() = runTest {
        coEvery { keysApi.getKeyExpiry("openAI") } returns KeyExpiryResponse(expiresAt = "never")

        val result = sut.fetchKeyState("openAI")

        val state = (result as Result.Success).data
        assertThat(state).isInstanceOf(KeyState.Set::class.java)
        val set = state as KeyState.Set
        assertThat(set.neverExpires).isTrue()
        assertThat(set.expiresAt).isNull()
        assertThat(set.wire).isEqualTo("never")
    }

    @Test
    fun `fetchKeyState maps future ISO timestamp to Set`() = runTest {
        val future = (Clock.System.now() + 7.days).toString()
        coEvery { keysApi.getKeyExpiry("openAI") } returns KeyExpiryResponse(expiresAt = future)

        val result = sut.fetchKeyState("openAI")

        val state = (result as Result.Success).data
        assertThat(state).isInstanceOf(KeyState.Set::class.java)
        val set = state as KeyState.Set
        assertThat(set.neverExpires).isFalse()
        assertThat(set.expiresAt).isNotNull()
        assertThat(set.wire).isEqualTo(future)
    }

    @Test
    fun `fetchKeyState maps past ISO timestamp to Expired`() = runTest {
        val past = (Clock.System.now() - 1.days).toString()
        coEvery { keysApi.getKeyExpiry("openAI") } returns KeyExpiryResponse(expiresAt = past)

        val result = sut.fetchKeyState("openAI")

        assertThat((result as Result.Success).data).isEqualTo(KeyState.Expired)
    }

    @Test
    fun `fetchKeyState propagates network errors as Result Error without swallowing`() = runTest {
        coEvery { keysApi.getKeyExpiry("openAI") } throws RuntimeException("boom")

        val result = sut.fetchKeyState("openAI")

        // Error must propagate as Result.Error so callers (chat fail-closed,
        // settings unwrap to Unset) can decide; the impl must not silently
        // map error -> Unset and discard the cause.
        assertThat(result).isInstanceOf(Result.Error::class.java)
    }

    @Test
    fun `fetchKeyState maps malformed wire to Unset`() = runTest {
        coEvery { keysApi.getKeyExpiry("openAI") } returns
            KeyExpiryResponse(expiresAt = "not-a-timestamp")

        val result = sut.fetchKeyState("openAI")

        assertThat((result as Result.Success).data).isEqualTo(KeyState.Unset)
    }

    @Test
    fun `keyInvalidations does not replay historical events to late subscribers`() = runTest {
        // replay=0 contract: a fresh subscriber attaching AFTER a prior
        // updateKey/deleteKey/deleteAllKeys does NOT receive the historical event.
        // Justification: every new ChatViewModel would otherwise trigger a fan-out
        // recompute on subscription even when the user never mutated a key.
        val request = UpdateKeyRequest(name = "openAI", value = "{}", expiresAt = "never")
        coEvery { keysApi.updateKey(request) } returns Unit

        // Fire the mutation BEFORE any subscriber attaches.
        sut.updateKey(request)
        advanceUntilIdle()

        // Late subscriber attaches.
        val emitted = mutableListOf<KeyInvalidation>()
        val collectorJob = launch { sut.keyInvalidations.collect { emitted.add(it) } }
        advanceUntilIdle()

        assertThat(emitted).isEmpty()
        collectorJob.cancel()
    }
}
