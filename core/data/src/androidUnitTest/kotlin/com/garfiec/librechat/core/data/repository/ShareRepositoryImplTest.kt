package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.network.api.ShareApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Re-sharing a chat hands back its live link instead of the server's 409: the lookup comes first,
 * and a conflict on create is read as "it exists after all".
 */
class ShareRepositoryImplTest {

    private val shareApi = mockk<ShareApi>()
    private val repository = ShareRepositoryImpl(
        shareApi = shareApi,
        serverUrlProvider = object : ServerUrlProvider {
            override fun getBaseUrl() = "https://chat.example.com/"
        },
        configRepository = mockk(),
    )

    private val noLink = ShareLinkLookup(success = false, shareId = null)
    private val liveLink = ShareLinkLookup(success = true, shareId = "existing")
    private val conflict = ApiException(statusCode = 409, message = "Share already exists", serverAuthored = true)

    @Test
    fun `an existing live link is returned without creating another`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns liveLink

        val result = repository.getOrCreateShareLink("convo") as Result.Success

        assertThat(result.data).isEqualTo("https://chat.example.com/share/existing")
        coVerify(exactly = 0) { shareApi.createShareLink(any(), any()) }
    }

    @Test
    fun `no live link creates one`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns noLink
        coEvery { shareApi.createShareLink("convo") } returns SharedLink(conversationId = "convo", shareId = "fresh")

        val result = repository.getOrCreateShareLink("convo") as Result.Success

        assertThat(result.data).isEqualTo("https://chat.example.com/share/fresh")
    }

    @Test
    fun `a failed lookup still creates, and a conflict then falls back to the lookup`() = runTest {
        coEvery { shareApi.getShareLink("convo") } throws IllegalStateException("lookup down") andThen liveLink
        coEvery { shareApi.createShareLink("convo") } throws conflict

        val result = repository.getOrCreateShareLink("convo") as Result.Success

        assertThat(result.data).isEqualTo("https://chat.example.com/share/existing")
    }

    @Test
    fun `a conflict with no link to show is still an error`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns noLink
        coEvery { shareApi.createShareLink("convo") } throws conflict

        val result = repository.getOrCreateShareLink("convo")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat((result as Result.Error).message).isEqualTo("Share already exists")
    }

    @Test
    fun `other create failures are not retried as lookups`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns noLink
        coEvery { shareApi.createShareLink("convo") } throws ApiException(statusCode = 403, message = "Forbidden")

        val result = repository.getOrCreateShareLink("convo")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 1) { shareApi.getShareLink("convo") }
    }
}
