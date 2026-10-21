package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.request.UpdateShareRequest
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.core.network.api.ShareApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A create that the server refuses as a duplicate (409) hands back the live link: the conflict is
 * read as "it exists after all". Updates always carry the file choice, a delete of a link that is
 * already gone succeeds, and every change bumps the revision.
 */
class ShareRepositoryImplTest {

    private val shareApi = mockk<ShareApi>()
    private val configRepository = mockk<ConfigRepository> {
        every { detectedBackendVersion } returns MutableStateFlow("0.8.8")
    }
    private val changes = ShareChangeSignal()
    private val repository = ShareRepositoryImpl(
        shareApi = shareApi,
        serverUrlProvider = object : ServerUrlProvider {
            override fun getBaseUrl() = "https://chat.example.com/"
        },
        configRepository = configRepository,
        shareChanges = changes,
    )

    private val noLink = ShareLinkLookup(success = false, shareId = null)
    private val liveLink = ShareLinkLookup(success = true, shareId = "existing")
    private val conflict = ApiException(statusCode = 409, message = "Share already exists", serverAuthored = true)
    private val notFound = ApiException(statusCode = 404, message = "Not found")

    @Test
    fun `a create conflict with no link to show is still an error`() = runTest {
        coEvery { shareApi.createShareLink("convo", null, true) } throws conflict
        coEvery { shareApi.getShareLink("convo") } returns noLink

        val result = repository.createShareLink("convo", targetMessageId = null, snapshotFiles = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat((result as Result.Error).message).isEqualTo("Share already exists")
        assertThat(changes.revision.value).isEqualTo(0)
    }

    @Test
    fun `other create failures are not retried as lookups`() = runTest {
        coEvery { shareApi.createShareLink("convo", null, true) } throws
            ApiException(statusCode = 403, message = "Forbidden")

        val result = repository.createShareLink("convo", targetMessageId = null, snapshotFiles = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { shareApi.getShareLink("convo") }
    }

    @Test
    fun `create sends the target and the file choice and bumps the revision`() = runTest {
        coEvery { shareApi.createShareLink("convo", "tail", false) } returns
            SharedLink(conversationId = "convo", shareId = "fresh")

        val result = repository.createShareLink("convo", targetMessageId = "tail", snapshotFiles = false)

        assertThat((result as Result.Success).data).isEqualTo("fresh")
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `a create conflict hands back the live link`() = runTest {
        coEvery { shareApi.createShareLink("convo", null, true) } throws conflict
        coEvery { shareApi.getShareLink("convo") } returns liveLink

        val result = repository.createShareLink("convo", targetMessageId = null, snapshotFiles = true)

        assertThat((result as Result.Success).data).isEqualTo("existing")
    }

    @Test
    fun `update sends the stored file choice in the body`() = runTest {
        coEvery { shareApi.updateShareLink("s1", UpdateShareRequest(null, false)) } returns
            SharedLink(conversationId = "convo", shareId = "s1")

        val result = repository.updateShareLink("s1", targetMessageId = null, snapshotFiles = false)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { shareApi.updateShareLink("s1", UpdateShareRequest(null, false)) }
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `an update of a link that is gone is an error, and lists are told`() = runTest {
        coEvery { shareApi.updateShareLink("gone", any()) } throws notFound

        val result = repository.updateShareLink("gone", targetMessageId = null, snapshotFiles = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `deleting a link that is already gone succeeds`() = runTest {
        coEvery { shareApi.deleteShareLink("gone") } throws notFound

        val result = repository.deleteShareLink("gone")

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `other delete failures stay errors and change nothing`() = runTest {
        coEvery { shareApi.deleteShareLink("s1") } throws ApiException(statusCode = 500, message = "Error")

        val result = repository.deleteShareLink("s1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(changes.revision.value).isEqualTo(0)
    }

    @Test
    fun `a delete bumps the revision`() = runTest {
        coEvery { shareApi.deleteShareLink("s1") } just runs

        repository.deleteShareLink("s1")

        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `the list sends the sort and a trimmed, capped search`() = runTest {
        coEvery { shareApi.getSharedLinks(any(), any(), any(), any(), any()) } returns SharedLinksResponse()
        val longSearch = "  " + "a".repeat(300) + "  "

        repository.getSharedLinksPaginated(cursor = "c1", sort = SharedLinksSort.TITLE, search = longSearch)

        coVerify {
            shareApi.getSharedLinks(
                cursor = "c1",
                sortBy = "title",
                sortDirection = "asc",
                search = "a".repeat(256),
                isPublic = null,
            )
        }
    }

    @Test
    fun `a blank search is omitted and the default sort is newest first`() = runTest {
        coEvery { shareApi.getSharedLinks(any(), any(), any(), any(), any()) } returns SharedLinksResponse()

        repository.getSharedLinksPaginated(search = "   ")

        coVerify {
            shareApi.getSharedLinks(
                cursor = null,
                sortBy = "createdAt",
                sortDirection = "desc",
                search = null,
                isPublic = null,
            )
        }
    }

    @Test
    fun `the share URL is the cleaned server URL plus the share path`() {
        assertThat(repository.shareUrl("abc")).isEqualTo("https://chat.example.com/share/abc")
    }

    @Test
    fun `a fork quotes the revision with the index`() = runTest {
        val response = mockk<ForkConversationResponse>()
        coEvery { shareApi.forkSharedConversation("s1", 3, "2026-10-21T10:00:00.000Z") } returns response

        val result = repository.forkSharedConversation("s1", 3, "2026-10-21T10:00:00.000Z")

        assertThat((result as Result.Success).data).isSameInstanceAs(response)
    }
}
