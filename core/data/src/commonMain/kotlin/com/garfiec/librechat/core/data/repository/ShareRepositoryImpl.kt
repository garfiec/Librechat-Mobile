package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.request.UpdateShareRequest
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.garfiec.librechat.core.model.share.MAX_SHARED_LINK_SEARCH_LENGTH
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.core.network.api.ShareApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import kotlinx.coroutines.flow.StateFlow

class ShareRepositoryImpl(
    private val shareApi: ShareApi,
    private val serverUrlProvider: ServerUrlProvider,
    private val configRepository: ConfigRepository,
    private val shareChanges: ShareChangeSignal,
) : ShareRepository {

    override val revision: StateFlow<Long> = shareChanges.revision

    private fun <T> Result<T>.bumpOnSuccess(): Result<T> = also { if (it is Result.Success) shareChanges.bump() }

    /**
     * Backends older than 0.8.7-rc1 filter `GET /api/share` on the `isPublic` query param and default
     * it to false when absent, so the Shared Links screen returns empty unless we send `isPublic=true`.
     * 0.8.7-rc1+ dropped that filter; the param is ignored there (verified), so it's harmless to send.
     *
     * Fail-safe on an unknown version: when the backend version hasn't resolved yet (cold start,
     * immediately post-login, or detection failure) we send `isPublic=true` so a legacy server still
     * returns links. The param is omitted only when the backend is *confirmed* >= 0.8.7-rc1. Returns null
     * to omit, which threads straight into [ShareApi.getSharedLinks].
     */
    private fun isPublicFilter(): Boolean? {
        val version = configRepository.detectedBackendVersion.value
        val isConfirmedModern = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")
        return if (isConfirmedModern) null else true
    }

    // Mirrors web's `buildShareLinkUrl` (scripts/mirrors.json `share-url-format`).
    override fun shareUrl(shareId: String): String =
        "${cleanServerUrl(serverUrlProvider.getBaseUrl())}/share/$shareId"

    override suspend fun lookupShareLink(conversationId: String): Result<ShareLinkLookup> =
        safeApiCall { shareApi.getShareLink(conversationId) }

    override suspend fun createShareLink(
        conversationId: String,
        targetMessageId: String?,
        snapshotFiles: Boolean?,
    ): Result<String> =
        safeApiCall { createOrAdopt(conversationId, targetMessageId, snapshotFiles) }.bumpOnSuccess()

    /** Creates a link, or hands back the live one when the server says one already exists. */
    private suspend fun createOrAdopt(
        conversationId: String,
        targetMessageId: String?,
        snapshotFiles: Boolean?,
    ): String =
        try {
            shareApi.createShareLink(conversationId, targetMessageId, snapshotFiles).shareId
                ?: throw IllegalStateException("Server returned no shareId")
        } catch (e: ApiException) {
            if (e.statusCode != HTTP_CONFLICT) throw e
            // A link appeared between the lookup and the create (another device, or the
            // lookup failed): the server's refusal is the answer, so hand that link back.
            shareApi.getShareLink(conversationId).liveShareId() ?: throw e
        }

    private fun ShareLinkLookup.liveShareId(): String? = shareId?.takeIf { success }

    override suspend fun getSharedLinksPaginated(
        cursor: String?,
        sort: SharedLinksSort,
        search: String?,
    ): Result<SharedLinksResponse> {
        val query = search?.trim()?.take(MAX_SHARED_LINK_SEARCH_LENGTH)?.trim()?.ifEmpty { null }
        return safeApiCall {
            shareApi.getSharedLinks(
                cursor = cursor,
                sortBy = sort.sortBy,
                sortDirection = sort.sortDirection,
                search = query,
                isPublic = isPublicFilter(),
            )
        }
    }

    override suspend fun updateShareLink(
        shareId: String,
        targetMessageId: String?,
        snapshotFiles: Boolean?,
    ): Result<SharedLink> =
        apiCallCatching<Result<SharedLink>>(
            {
                Result.Success(
                    shareApi.updateShareLink(shareId, UpdateShareRequest(targetMessageId, snapshotFiles)),
                )
            },
        ) { e ->
            // The link is gone: still an error for the caller, but a list holding it is now stale.
            if ((e as? ApiException)?.statusCode == HTTP_NOT_FOUND) shareChanges.bump()
            e.toSafeError()
        }.bumpOnSuccess()

    override suspend fun deleteShareLink(shareId: String): Result<Unit> =
        apiCallCatching<Result<Unit>>(
            {
                shareApi.deleteShareLink(shareId)
                Result.Success(Unit)
            },
        ) { e ->
            // Already gone is what the caller asked for. Not routed through safeApiCall, which
            // would log an expected outcome as an error.
            if ((e as? ApiException)?.statusCode == HTTP_NOT_FOUND) Result.Success(Unit) else e.toSafeError()
        }.bumpOnSuccess()

    override suspend fun getSharedSnapshot(shareId: String): Result<SharedConversationSnapshot> =
        safeApiCall { shareApi.getSharedSnapshot(shareId) }

    override suspend fun downloadSharedFile(shareId: String, fileId: String): Result<ByteArray> =
        safeApiCall { shareApi.downloadSharedFile(shareId, fileId) }

    override suspend fun forkSharedConversation(
        shareId: String,
        targetMessageIndex: Int?,
        shareRevision: String?,
    ): Result<ForkConversationResponse> {
        // Deliberately not version-gated: a pre-0.8.8 server 404s, which safeApiCall already
        // turns into the error the caller has to handle anyway, and the call only ever happens
        // because a user pressed a button — there is nothing to fail closed on ahead of time.
        return safeApiCall {
            shareApi.forkSharedConversation(shareId, targetMessageIndex, shareRevision)
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
        const val HTTP_NOT_FOUND = 404
    }
}
