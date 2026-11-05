package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.code.CodeRunRequest
import com.garfiec.librechat.core.model.code.CodeRunResult
import com.garfiec.librechat.core.model.code.codeRunsByKey
import com.garfiec.librechat.core.model.code.toResult
import com.garfiec.librechat.core.network.api.AgentsApi

/**
 * Running a reply's fenced code blocks (web `RunCode.tsx`). Network-direct, no cache: the stored
 * runs are the server's tool-call records, read once per conversation by the chat.
 */
interface CodeRunRepository {
    suspend fun run(request: CodeRunRequest): Result<CodeRunResult>

    /** Every stored run of [conversationId], keyed by `codeRunKey`, oldest first in each list. */
    suspend fun runs(conversationId: String): Result<Map<String, List<CodeRunResult>>>
}

class CodeRunRepositoryImpl(private val api: AgentsApi) : CodeRunRepository {
    override suspend fun run(request: CodeRunRequest): Result<CodeRunResult> =
        safeApiCall { api.runCode(request).toResult() }

    override suspend fun runs(conversationId: String): Result<Map<String, List<CodeRunResult>>> =
        safeApiCall { api.getToolCalls(conversationId).codeRunsByKey() }
}
