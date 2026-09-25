package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.trace.TraceAvailability
import com.garfiec.librechat.core.model.trace.TracePage
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import com.garfiec.librechat.core.network.api.TracesApi
import kotlinx.coroutines.delay

class TraceRepositoryImpl(
    private val tracesApi: TracesApi,
    private val configRepository: ConfigRepository,
) : TraceRepository {

    override fun isRuledOutForServer(): Boolean =
        BackendVersion.featureSupport(
            configRepository.detectedBackend.value,
            minVersion = MIN_VERSION,
        ).isRuledOut

    override suspend fun resolveAvailability(conversationId: String): Result<TraceAvailability> {
        var errorRetries = 0
        var waits = 0
        while (true) {
            val result = safeApiCall { tracesApi.getAvailability(conversationId) }
            when (result) {
                is Result.Success -> {
                    val retryAfterMs = result.data.retryAfterMs
                    // The backend sets this only while it cannot decide yet, and the count is
                    // bounded so a deployment that never settles stops asking rather than polling
                    // the route for as long as the conversation stays open.
                    if (result.data.available || retryAfterMs == null || retryAfterMs <= 0 ||
                        waits >= MAX_WAITS
                    ) {
                        return result
                    }
                    waits++
                    delay(retryAfterMs)
                }

                is Result.Error -> {
                    // A hidden control has nothing to report, so a transient fault retries quietly
                    // and anything the server decided is taken at its word.
                    val status = (result.exception as? ApiException)?.statusCode
                    val transient = status == null || status >= HTTP_SERVER_ERROR
                    if (!transient || errorRetries >= MAX_ERROR_RETRIES) return result
                    errorRetries++
                    delay(RETRY_BASE_MS shl (errorRetries - 1))
                }

                is Result.Loading -> return result
            }
        }
    }

    override suspend fun getRecords(conversationId: String, cursor: String?): Result<TracePage> =
        safeApiCall { tracesApi.getRecords(conversationId, cursor) }

    override suspend fun getRecord(
        conversationId: String,
        recordId: String,
        messageId: String,
        sourceId: String?,
    ): Result<TraceRecordDetail> =
        safeApiCall { tracesApi.getRecord(conversationId, recordId, messageId, sourceId) }

    private companion object {
        const val MIN_VERSION = "0.8.8-rc3"
        const val MAX_WAITS = 10
        const val MAX_ERROR_RETRIES = 3
        const val RETRY_BASE_MS = 1_000L
        const val HTTP_SERVER_ERROR = 500
    }
}
