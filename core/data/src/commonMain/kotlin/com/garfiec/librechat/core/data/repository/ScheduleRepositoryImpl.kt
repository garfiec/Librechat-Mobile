package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.schedule.CreateScheduleRequest
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.garfiec.librechat.core.model.schedule.ScheduleRunNowResponse
import com.garfiec.librechat.core.model.schedule.UpdateScheduleRequest
import com.garfiec.librechat.core.network.api.SchedulesApi

class ScheduleRepositoryImpl(
    private val schedulesApi: SchedulesApi,
) : ScheduleRepository {

    override suspend fun listSchedules(): Result<ScheduleListResponse> =
        safeApiCall { schedulesApi.listSchedules() }

    override suspend fun getSchedule(id: String): Result<Schedule> =
        safeApiCall { schedulesApi.getSchedule(id) }

    override suspend fun createSchedule(request: CreateScheduleRequest): Result<Schedule> =
        safeApiCall { schedulesApi.createSchedule(request) }

    override suspend fun updateSchedule(
        id: String,
        request: UpdateScheduleRequest,
    ): Result<Schedule> {
        // A no-op edit is a 400 server-side. Answering it here keeps a Save on an untouched form
        // from reporting a failure the user cannot act on.
        if (request.isEmpty) return safeApiCall { schedulesApi.getSchedule(id) }
        return safeApiCall { schedulesApi.updateSchedule(id, request) }
    }

    override suspend fun deleteSchedule(id: String): Result<Unit> =
        safeApiCall { schedulesApi.deleteSchedule(id) }.let { result ->
            // `202` means a live run is still draining, which is still a successful delete from
            // the caller's point of view — the row will not fire again either way.
            when (result) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> result
                is Result.Loading -> Result.Loading
            }
        }

    override suspend fun runScheduleNow(id: String): Result<ScheduleRunNowResponse> =
        safeApiCall { schedulesApi.runScheduleNow(id) }
}
