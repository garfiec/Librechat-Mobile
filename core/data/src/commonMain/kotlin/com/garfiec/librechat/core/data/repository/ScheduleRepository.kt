package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.schedule.CreateScheduleRequest
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.garfiec.librechat.core.model.schedule.ScheduleRunNowResponse
import com.garfiec.librechat.core.model.schedule.UpdateScheduleRequest

/**
 * Scheduled chats. Server is the sole source of truth — no local cache, like
 * [ProjectRepository] and [McpRepository].
 *
 * Caching would be actively wrong here: `nextRunAt`, `lastRun` and `inFlight` all change without
 * this client doing anything, and a stale card claiming a run is still generating is worse than a
 * spinner.
 */
interface ScheduleRepository {

    /** The list AND the deployment's policy, which the editor needs before it can offer choices. */
    suspend fun listSchedules(): Result<ScheduleListResponse>

    suspend fun getSchedule(id: String): Result<Schedule>

    suspend fun createSchedule(request: CreateScheduleRequest): Result<Schedule>

    /**
     * Applies only what [request] carries.
     *
     * An empty request is answered locally with a read of the unchanged row — success, not a
     * refusal — because the server 400s an update that changes nothing and a Save on an untouched
     * form would then report a failure the user cannot act on. The caller therefore cannot tell an
     * empty edit from an applied one, which is deliberate: both leave the server as it was.
     */
    suspend fun updateSchedule(id: String, request: UpdateScheduleRequest): Result<Schedule>

    suspend fun deleteSchedule(id: String): Result<Unit>

    suspend fun runScheduleNow(id: String): Result<ScheduleRunNowResponse>
}
