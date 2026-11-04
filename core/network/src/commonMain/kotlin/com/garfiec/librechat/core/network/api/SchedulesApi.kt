package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.schedule.CreateScheduleRequest
import com.garfiec.librechat.core.model.schedule.RevokeScheduleMcpConsentRequest
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleDeleteResponse
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentView
import com.garfiec.librechat.core.model.schedule.ScheduleRunNowResponse
import com.garfiec.librechat.core.model.schedule.UpdateScheduleRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path
import kotlinx.serialization.json.JsonObject

/**
 * Scheduled chats (v0.8.8-rc2).
 *
 * **Mounted is not enabled.** The router is registered unconditionally, so a 404 here proves only
 * that the server predates the feature — never that a deployment has it turned off. Enablement is
 * `interface.schedules` plus the SCHEDULES permission, asked before any of this is called.
 *
 * Only [listSchedules] answers a wrapper; every other route answers a bare [Schedule].
 */
class SchedulesApi constructor(
    private val client: HttpClient,
) {
    suspend fun listSchedules(): ScheduleListResponse =
        client.get { url { path("api/schedules") } }.body()

    suspend fun getSchedule(id: String): Schedule =
        client.get { url { path("api/schedules/${id.encodeURLPathPart()}") } }.body()

    /** `201`, or the existing row when `clientRequestId` has been seen before. */
    suspend fun createSchedule(request: CreateScheduleRequest): Schedule =
        client.post {
            url { path("api/schedules") }
            setBody(request)
        }.body()

    /** Sends only the fields the caller set; everything omitted is left as it is. */
    suspend fun updateSchedule(id: String, request: UpdateScheduleRequest): Schedule =
        client.patch {
            url { path("api/schedules/${id.encodeURLPathPart()}") }
            setBody(request)
        }.body()

    /** `200` erased, `202` still draining a live run. Never touches the schedule engine. */
    suspend fun deleteSchedule(id: String): ScheduleDeleteResponse =
        client.delete { url { path("api/schedules/${id.encodeURLPathPart()}") } }.body()

    suspend fun runScheduleNow(id: String): ScheduleRunNowResponse =
        client.post { url { path("api/schedules/${id.encodeURLPathPart()}/run") } }.body()

    /**
     * The MCP consent view (v0.8.9-rc1). Readable by anyone who can see the schedule (no
     * SCHEDULES permission check on this route); confirm needs USE + CREATE.
     */
    suspend fun getMcpConsent(id: String): ScheduleMcpConsentView =
        client.get { url { path("api/schedules/${id.encodeURLPathPart()}/mcp-consent") } }.body()

    /** [body] from `confirmScheduleMcpConsentBody`, which keeps the required `expectedRevision: null`. */
    suspend fun confirmMcpConsent(id: String, body: JsonObject): ScheduleMcpConsentView =
        client.post {
            url { path("api/schedules/${id.encodeURLPathPart()}/mcp-consent") }
            setBody(body)
        }.body()

    /** `204` with no body. */
    suspend fun revokeMcpConsent(id: String, request: RevokeScheduleMcpConsentRequest) {
        client.delete {
            url { path("api/schedules/${id.encodeURLPathPart()}/mcp-consent") }
            setBody(request)
        }
    }
}
