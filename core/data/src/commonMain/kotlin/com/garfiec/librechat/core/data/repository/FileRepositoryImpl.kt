package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.FeatureSupport
import com.garfiec.librechat.core.common.di.ioDispatcher
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.onApiDispatcher
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.pdf.PDF_DECRYPTION_UNAVAILABLE_MESSAGE
import com.garfiec.librechat.core.data.pdf.PDF_INCORRECT_PASSWORD_MESSAGE
import com.garfiec.librechat.core.data.pdf.PDF_PASSWORD_PROTECTED_MESSAGE
import com.garfiec.librechat.core.data.pdf.PdfDecryptionUnavailableException
import com.garfiec.librechat.core.data.pdf.PdfNormalizeResult
import com.garfiec.librechat.core.data.pdf.PdfNormalizer
import com.garfiec.librechat.core.data.pdf.PdfPasswordProtectedException
import com.garfiec.librechat.core.data.pdf.hasEncryptKey
import com.garfiec.librechat.core.data.pdf.isPdfUpload
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.core.model.request.DeleteFilesRequest
import com.garfiec.librechat.core.model.response.DeleteFilesResponse
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.model.response.FileUploadConfig
import com.garfiec.librechat.core.network.api.FILES_USAGE_MAX_IDS
import com.garfiec.librechat.core.network.api.FilesApi
import com.garfiec.librechat.core.network.api.FilesExtApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FileRepositoryImpl(
    private val filesApi: FilesApi,
    private val filesExtApi: FilesExtApi,
    private val configRepository: ConfigRepository,
    private val pdfNormalizer: PdfNormalizer,
) : FileRepository {

    override suspend fun getFiles(): Result<List<FileObject>> =
        safeApiCall { filesApi.getFiles() }

    override suspend fun getFileConfig(): Result<FileUploadConfig> =
        safeApiCall { filesApi.getFileConfig() }

    override suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        onProgress: ((Float) -> Unit)?,
    ): Result<FileObject> =
        withUploadBytes(bytes, filename, type, pdfPassword = null) { uploadBytes ->
            filesApi.uploadFile(
                bytes = uploadBytes,
                filename = filename,
                type = type,
                // file_id and endpoint are required by the backend; provide defaults
                fileId = Uuid.random().toString(),
                endpoint = "agents",
                onProgress = onProgress,
            )
        }

    override suspend fun uploadFile(
        bytes: ByteArray,
        filename: String,
        type: String,
        fileId: String?,
        endpoint: String?,
        model: String?,
        agentId: String?,
        toolResource: String?,
        messageFile: Boolean?,
        width: Int?,
        height: Int?,
        onProgress: ((Float) -> Unit)?,
        pdfPassword: String?,
    ): Result<FileObject> =
        withUploadBytes(bytes, filename, type, pdfPassword) { uploadBytes ->
            filesApi.uploadFile(
                bytes = uploadBytes,
                filename = filename,
                type = type,
                fileId = fileId ?: Uuid.random().toString(),
                endpoint = endpoint,
                model = model,
                agentId = agentId,
                toolResource = toolResource,
                messageFile = messageFile,
                width = width,
                height = height,
                onProgress = onProgress,
            )
        }

    /**
     * Uploads what the server will accept: an encrypted PDF goes up without its encryption layer,
     * because the server refuses any encrypted PDF for Anthropic at send time.
     *
     * A copy-protected PDF (empty user password) is decrypted silently, and fails open — a broken
     * normalizer must never block an upload that would otherwise work. A PDF that needs a password
     * is refused with [PdfPasswordProtectedException] until the caller supplies [pdfPassword];
     * with one, a failure is reported rather than uploading a file nobody can read.
     */
    private suspend fun withUploadBytes(
        bytes: ByteArray,
        filename: String,
        type: String,
        pdfPassword: String?,
        upload: suspend (ByteArray) -> FileObject,
    ): Result<FileObject> =
        when (val prepared = withContext(ioDispatcher) { preparePdf(bytes, filename, type, pdfPassword) }) {
            is PreparedUpload.Send -> safeApiCall { upload(prepared.bytes) }
            is PreparedUpload.Refuse -> prepared.error
        }

    private sealed interface PreparedUpload {
        class Send(val bytes: ByteArray) : PreparedUpload
        class Refuse(val error: Result.Error) : PreparedUpload
    }

    // Never log pdfPassword.
    @Suppress("TooGenericExceptionCaught")
    private fun preparePdf(bytes: ByteArray, filename: String, type: String, pdfPassword: String?): PreparedUpload {
        if (!isPdfUpload(type, filename, bytes) || !hasEncryptKey(bytes)) return PreparedUpload.Send(bytes)
        val result = try {
            pdfNormalizer.normalize(bytes, pdfPassword)
        } catch (e: Exception) {
            PdfNormalizeResult.Failed(e)
        }
        val decryptedBytes = (result as? PdfNormalizeResult.Decrypted)?.bytes?.takeUnless(::hasEncryptKey)
        return when {
            result == PdfNormalizeResult.Unchanged -> PreparedUpload.Send(bytes)
            decryptedBytes != null -> {
                Logger.i { "Removed PDF encryption from $filename (${bytes.size} -> ${decryptedBytes.size} bytes)" }
                PreparedUpload.Send(decryptedBytes)
            }
            result == PdfNormalizeResult.PasswordUnsupported -> {
                Logger.i { "PDF upload refused: $filename needs a password this device cannot decrypt with" }
                PreparedUpload.Refuse(
                    Result.Error(
                        exception = PdfDecryptionUnavailableException(cause = null),
                        message = PDF_DECRYPTION_UNAVAILABLE_MESSAGE,
                    ),
                )
            }
            result == PdfNormalizeResult.NeedsPassword -> {
                Logger.i { "PDF upload held: $filename needs a password (retry=${pdfPassword != null})" }
                val incorrect = pdfPassword != null
                PreparedUpload.Refuse(
                    Result.Error(
                        exception = PdfPasswordProtectedException(incorrectPassword = incorrect),
                        message = if (incorrect) PDF_INCORRECT_PASSWORD_MESSAGE else PDF_PASSWORD_PROTECTED_MESSAGE,
                    ),
                )
            }
            pdfPassword != null -> {
                val cause = (result as? PdfNormalizeResult.Failed)?.cause
                Logger.w { "PDF could not be decrypted with its password: $filename ($cause)" }
                PreparedUpload.Refuse(
                    Result.Error(
                        exception = PdfDecryptionUnavailableException(cause),
                        message = PDF_DECRYPTION_UNAVAILABLE_MESSAGE,
                    ),
                )
            }
            else -> {
                Logger.w { "PDF left encrypted for $filename (${(result as? PdfNormalizeResult.Failed)?.cause}); uploading original" }
                PreparedUpload.Send(bytes)
            }
        }
    }

    /**
     * Deletes [files], reporting every entry the route would silently discard as a FAILURE.
     *
     * `DELETE /api/files` drops each entry with a falsy `filepath` before it looks at anything
     * else, and answers 204 with no body when that leaves nothing — so an unnameable entry is
     * never listed in `failedFileIds` and a caller diffing "asked minus failed" reads it as
     * deleted. Enforced here rather than at each caller because the rule belongs to the route —
     * every caller that diffs its own request against `failedFileIds` needs it, and none of them
     * can see that the route dropped the entry.
     */
    override suspend fun deleteFiles(
        files: List<DeleteFileEntry>,
        agentId: String?,
        toolResource: String?,
    ): Result<DeleteFilesResponse> {
        val (deletable, unnameable) = files.partition { it.filepath.isNotBlank() }
        if (deletable.isEmpty()) {
            return Result.Success(
                DeleteFilesResponse(failedFileIds = unnameable.map { it.fileId }),
            )
        }
        val result = safeApiCall {
            filesApi.deleteFiles(
                DeleteFilesRequest(
                    files = deletable,
                    agentId = agentId,
                    toolResource = toolResource,
                ),
            )
        }
        if (unnameable.isEmpty()) return result
        return when (result) {
            is Result.Success -> Result.Success(
                result.data.copy(
                    failedFileIds = result.data.failedFileIds + unnameable.map { it.fileId },
                ),
            )
            else -> result
        }
    }

    /**
     * Prefers the direct/presigned download URL (v0.8.6 — S3/CloudFront) so the
     * bytes come straight from the CDN instead of proxying through LibreChat.
     * Falls back to the `/download` proxy whenever the URL path is unavailable:
     * the endpoint 501s for sources with no direct-URL strategy (local storage),
     * 400s for OpenAI-storage files missing a model, and any transport error on
     * the CDN fetch should still yield a working download via the proxy. Only
     * the final proxy result surfaces through [safeApiCall] error mapping; the
     * URL attempt's failures are swallowed (they're expected on non-CDN servers).
     */
    override suspend fun downloadFile(userId: String, fileId: String): Result<ByteArray> {
        try {
            // onApiDispatcher, not safeApiCall: safeApiCall would log every one of these expected
            // failures at error level. The dispatcher hop is still required (#326).
            return onApiDispatcher {
                val urlResponse = filesApi.getDownloadUrl(userId, fileId)
                Result.Success(filesApi.downloadFromUrl(urlResponse.url))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Expected on local-storage / OpenAI-storage / non-CDN servers, or a
            // transient CDN failure — fall through to the server-proxy download.
        }
        return safeApiCall { filesApi.downloadFile(userId, fileId) }
    }

    override suspend fun getAgentFiles(agentId: String): Result<List<FileObject>> =
        safeApiCall { filesExtApi.getAgentFiles(agentId) }

    /**
     * Polls the preview endpoint until terminal or the attempt budget is spent.
     * Budget (~POLL_MAX_ATTEMPTS × POLL_INTERVAL_MS ≈ 60s) brackets the server's
     * lazy-sweep cutoff, so a stuck-pending record resolves to `failed` within
     * the loop rather than spinning forever. A non-terminal final poll is still
     * returned as Success(status="pending") — the caller decides how to surface
     * a slow/never-resolving preview. Transport errors surface via [safeApiCall].
     */
    override suspend fun pollFilePreview(fileId: String): Result<FilePreviewResponse> {
        var attempt = 0
        while (true) {
            when (val result = safeApiCall { filesApi.getFilePreview(fileId) }) {
                is Result.Success -> {
                    if (result.data.isTerminal || attempt >= POLL_MAX_ATTEMPTS) return result
                }
                is Result.Error -> return result
                is Result.Loading -> { /* safeApiCall never emits Loading */ }
            }
            attempt++
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * What a single 404 from `POST /api/files/usage` taught us, on a server the version gate could
     * not place. `null` until the first such touch settles; false latches the route off for the
     * rest of this server session, true confirms it. Reset by [clear] on account/server switch.
     */
    @Volatile
    private var usageHoldProbeVerdict: Boolean? = null

    /**
     * True only when the route is KNOWN to be missing — a build commit that resolved to a tag
     * below v0.8.8-rc1, or a probe that already 404'd. An unplaceable server is not ruled out.
     *
     * Both directions cost something, which is why proof and doubt are separated. Calling a
     * server that lacks the route is not a free 404: pre-0.8.8 servers apply `fileUploadIpLimiter`
     * + `fileUploadUserLimiter` to every POST under `/api/files` except `/speech` (the `/usage`
     * exemption arrived with the route), so it spends real upload quota and a violation score. But
     * NOT calling a server that has it lets the upload-window reaper collect an attachment out
     * from under a queued message, and the send then references a file the server has deleted.
     *
     * So: rule the route out on proof, probe on doubt. A server the gate cannot place gets exactly
     * one touch, and its 404 latches the suppression.
     */
    private fun usageHoldRuledOut(): Boolean =
        usageHoldProbeVerdict == false || usageHoldSupport().isRuledOut

    private fun usageHoldSupport(): FeatureSupport = BackendVersion.featureSupport(
        configRepository.detectedBackend.value,
        minVersion = "0.8.8-rc1",
    )

    override fun supportsUsageHold(): Boolean = !usageHoldRuledOut()

    override suspend fun markFilesUsed(fileIds: List<String>): Result<Unit> {
        if (usageHoldRuledOut()) {
            return Result.Success(Unit)
        }
        val ids = fileIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return Result.Success(Unit)
        // Whether THIS call is the one discovering the answer. A server the version gate already
        // placed as PRESENT is not probing: a 404 from it is a proxy or a deployment oddity, not
        // evidence about the release, and must not permanently disable the hold.
        val probing = usageHoldProbeVerdict == null && !usageHoldSupport().isPresent
        // The route caps a call at FILES_USAGE_MAX_IDS and 400s the whole batch past it, so
        // chunk rather than let one over-long queue item silently forfeit every touch in it.
        for (chunk in ids.chunked(FILES_USAGE_MAX_IDS)) {
            val result = safeApiCall { filesApi.markFilesUsed(chunk) }
            if (result is Result.Error) {
                if (probing && (result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                    usageHoldProbeVerdict = false
                    // Best-effort by contract (see [markFilesUsed]): a missing route is not a
                    // failure to report upward.
                    return Result.Success(Unit)
                }
                return result
            }
        }
        if (probing) usageHoldProbeVerdict = true
        return Result.Success(Unit)
    }

    override fun clear() {
        usageHoldProbeVerdict = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_MAX_ATTEMPTS = 30
        const val HTTP_NOT_FOUND = 404
    }
}
