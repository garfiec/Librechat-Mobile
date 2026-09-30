package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.FeatureSupport
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.background.BackgroundTaskCancelResponse
import com.garfiec.librechat.core.model.background.BackgroundTaskIndex
import com.garfiec.librechat.core.network.api.ConversationsApi
import kotlin.concurrent.Volatile

class BackgroundTaskRepositoryImpl(
    private val conversationsApi: ConversationsApi,
    private val configRepository: ConfigRepository,
) : BackgroundTaskRepository {

    /**
     * Set when a probe 404'd. Its own field rather than a "supported" flag, which would also read
     * false before the first probe and so re-probe on every open. Reset by [clear].
     */
    @Volatile
    private var routeMissingByProbe = false

    private fun support(): FeatureSupport = BackendVersion.featureSupport(
        configRepository.detectedBackend.value,
        minVersion = MIN_VERSION,
        landedDate = LANDED_DATE,
    )

    override fun isRuledOutForServer(): Boolean = routeMissingByProbe || support().isRuledOut

    override suspend fun getTasks(conversationId: String): Result<BackgroundTaskIndex?> {
        if (isRuledOutForServer()) return Result.Success(null)
        // A 404 from a server the gate already placed as PRESENT is a proxy or deployment oddity,
        // not evidence about the release, so only an unconfirmed server's 404 latches.
        val probing = !support().isPresent
        return when (val result = safeApiCall { conversationsApi.getBackgroundTasks(conversationId) }) {
            is Result.Success -> result
            is Result.Error -> {
                if (probing && (result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                    routeMissingByProbe = true
                    Result.Success(null)
                } else {
                    result
                }
            }
            is Result.Loading -> Result.Loading
        }
    }

    override suspend fun cancel(conversationId: String, taskIds: List<String>?): Result<BackgroundTaskCancelResponse> =
        safeApiCall { conversationsApi.cancelBackgroundTasks(conversationId, taskIds) }

    override fun clear() {
        routeMissingByProbe = false
    }

    private companion object {
        const val MIN_VERSION = "0.8.8"

        /**
         * UTC landing day of the routes (e32e6b540ecd). Day-granular: a dev build from earlier that
         * same day is placed PRESENT, so its 404 does not latch and every bind, run settle and sheet
         * open repeats one 404. Harmless (the chip stays hidden); the day after would instead hide
         * the chip from same-day builds that have the routes.
         */
        const val LANDED_DATE = "2026-09-25"
        const val HTTP_NOT_FOUND = 404
    }
}
