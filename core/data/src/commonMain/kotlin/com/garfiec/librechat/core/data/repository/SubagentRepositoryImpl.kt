package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.subagent.SubagentIndex
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import com.garfiec.librechat.core.network.api.SubagentsApi

class SubagentRepositoryImpl(
    private val subagentsApi: SubagentsApi,
    private val configRepository: ConfigRepository,
) : SubagentRepository {

    /**
     * Suppressed only on a build commit that resolved to a tag BELOW the routes — never on a
     * server this client cannot place. That population is the one most likely to have them (a dev
     * build still reporting the previous release, or one built past the commit-map pin), and the
     * cost of asking is a single GET made only when a user opens the section.
     */
    override fun isRuledOutForServer(): Boolean =
        BackendVersion.featureSupport(
            configRepository.detectedBackend.value,
            minVersion = MIN_VERSION,
        ).isRuledOut

    override suspend fun getChildren(parentConversationId: String): Result<SubagentIndex> =
        safeApiCall { subagentsApi.getIndex(parentConversationId) }

    override suspend fun getThread(
        parentConversationId: String,
        threadId: String,
    ): Result<SubagentThreadView> =
        safeApiCall { subagentsApi.getThread(parentConversationId, threadId) }

    override suspend fun getThreadTask(
        parentConversationId: String,
        threadId: String,
        taskId: String,
    ): Result<SubagentThreadView> =
        safeApiCall { subagentsApi.getThreadTask(parentConversationId, threadId, taskId) }

    override suspend fun getOlderPage(
        parentConversationId: String,
        threadId: String,
        cursor: String,
    ): Result<SubagentThreadView> =
        safeApiCall { subagentsApi.getThreadPage(parentConversationId, threadId, cursor) }

    private companion object {
        const val MIN_VERSION = "0.8.8-rc2"
    }
}
