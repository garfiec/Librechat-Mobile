package com.garfiec.librechat.core.model.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ScheduleWireTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun decodes_the_list_route_with_its_limits() {
        // The list is the ONLY route that wraps; the single-schedule routes answer a bare
        // schedule, so a caller that expects the wrapper everywhere fails to decode a create.
        val body = """
            {
              "schedules": [
                {
                  "id": "sched_1", "user": "u1", "name": "Morning digest",
                  "prompt": "Summarise my inbox", "agent_id": "agent_abc",
                  "cadence": {"frequency": "daily", "hour": 7, "minute": 30},
                  "timezone": "Europe/London", "target": "new",
                  "chatProjectId": "proj_1", "enabled": true,
                  "nextRunAt": "2026-09-19T06:30:00.000Z",
                  "runCount": 12, "failureCount": 0, "configRevision": 3,
                  "createdAt": "2026-09-01T00:00:00.000Z",
                  "updatedAt": "2026-09-18T00:00:00.000Z",
                  "inFlight": [{"conversationId": "convo-9"}]
                }
              ],
              "limits": {"maxPerUser": 5, "minIntervalMinutes": 15, "requireProject": true}
            }
        """.trimIndent()

        val response = json.decodeFromString<ScheduleListResponse>(body)
        val schedule = response.schedules.single()

        assertEquals("sched_1", schedule.id)
        assertEquals(ScheduleFrequency.DAILY, schedule.cadence.frequency)
        assertEquals(7, schedule.cadence.hour)
        assertEquals("convo-9", schedule.inFlight?.single()?.conversationId)
        assertEquals(3, schedule.configRevision)
        assertEquals(5, response.limits.maxPerUser)
        assertTrue(response.limits.requireProject)
        assertNull(response.limits.projectId)
    }

    @Test
    fun decodes_a_cron_cadence_without_the_structured_fields() {
        val schedule = json.decodeFromString<Schedule>(
            """
            {"id": "sched_2", "name": "Shift report", "prompt": "p", "agent_id": "a",
             "cadence": {"frequency": "cron", "expression": "0 9,17 * * 1-5"},
             "timezone": "UTC", "enabled": true, "runCount": 0, "failureCount": 0}
            """.trimIndent(),
        )

        assertTrue(schedule.cadence.isCron)
        assertEquals("0 9,17 * * 1-5", schedule.cadence.expression)
        assertNull(schedule.cadence.hour)
    }

    @Test
    fun a_disabled_schedule_decodes_its_reason_and_mcp_outcomes() {
        val schedule = json.decodeFromString<Schedule>(
            """
            {"id": "sched_3", "name": "n", "prompt": "p", "agent_id": "a",
             "cadence": {"frequency": "daily", "hour": 9, "minute": 0},
             "timezone": "UTC", "enabled": false,
             "disabledReason": "mcp_reauth_required",
             "lastRun": {"status": "error", "firedAt": "2026-09-17T09:00:00.000Z",
                         "mcp": [{"server": "github", "agentId": "agent_abc",
                                  "status": "mcp_reauth_required"}]},
             "runCount": 4, "failureCount": 2}
            """.trimIndent(),
        )

        assertEquals(ScheduleDisabledReason.MCP_REAUTH_REQUIRED, schedule.disabledReason)
        assertEquals(ScheduleMcpStatus.REAUTH_REQUIRED, schedule.lastRun?.mcp?.single()?.status)
        assertEquals("github", schedule.lastRun?.mcp?.single()?.server)
    }

    @Test
    fun an_unknown_status_degrades_one_row_instead_of_failing_the_list() {
        val response = json.decodeFromString<ScheduleListResponse>(
            """
            {"schedules": [
               {"id": "s1", "name": "n", "prompt": "p", "agent_id": "a",
                "cadence": {"frequency": "some_future_cadence"},
                "timezone": "UTC", "enabled": true, "runCount": 0, "failureCount": 0,
                "disabledReason": "some_future_reason",
                "lastRun": {"status": "some_future_status"}}
             ],
             "limits": {"maxPerUser": 1, "minIntervalMinutes": 1, "requireProject": false}}
            """.trimIndent(),
        )

        assertEquals(1, response.schedules.size)
        assertEquals("some_future_cadence", response.schedules.single().cadence.frequency)
    }

    @Test
    fun a_create_body_sends_only_what_was_set() {
        // `encodeDefaults = false` plus nullable optionals: the server's zod schema rejects a
        // `null` where it expects an absent key, and a structured cadence carrying `expression`.
        val encoded = json.encodeToString(
            CreateScheduleRequest(
                name = "Morning digest",
                prompt = "Summarise my inbox",
                agentId = "agent_abc",
                cadence = ScheduleCadence.structured(ScheduleFrequency.DAILY, hour = 7, minute = 30),
                timezone = "Europe/London",
                clientRequestId = "req-1",
            ),
        )

        assertEquals(
            """{"name":"Morning digest","prompt":"Summarise my inbox","agent_id":"agent_abc",""" +
                """"cadence":{"frequency":"daily","hour":7,"minute":30},""" +
                """"timezone":"Europe/London","clientRequestId":"req-1"}""",
            encoded,
        )
    }

    @Test
    fun a_cron_create_body_carries_no_structured_fields() {
        val encoded = json.encodeToString(
            CreateScheduleRequest(
                name = "n",
                prompt = "p",
                agentId = "a",
                cadence = ScheduleCadence.cron("0 9 * * 1-5"),
                timezone = "UTC",
                clientRequestId = "req-2",
            ),
        )

        assertTrue(encoded.contains(""""cadence":{"frequency":"cron","expression":"0 9 * * 1-5"}"""))
        assertTrue(!encoded.contains("\"hour\""))
        assertTrue(!encoded.contains("\"minute\""))
    }

    @Test
    fun an_update_body_omits_everything_the_user_did_not_change() {
        // THE safety property of the editor. `cadence` is sent whole, so a body that always
        // included it would rewrite a cron expression into whatever the structured controls
        // happen to hold — and `file_ids` would detach attachments this client never managed.
        val encoded = json.encodeToString(
            UpdateScheduleRequest(name = "Renamed", expectedConfigRevision = 3),
        )

        assertEquals("""{"name":"Renamed","expectedConfigRevision":3}""", encoded)
    }

    @Test
    fun an_update_that_changes_nothing_is_recognised_before_it_is_sent() {
        assertTrue(UpdateScheduleRequest().isEmpty)
        assertTrue(UpdateScheduleRequest(expectedConfigRevision = 3).isEmpty)
        assertTrue(!UpdateScheduleRequest(enabled = false).isEmpty)
    }

    @Test
    fun a_cleared_project_is_distinguishable_from_an_unchanged_one() {
        // `chatProjectId: null` CLEARS the scope server-side, so it has to travel as an explicit
        // null — which `explicitNulls = false` would drop. The editor therefore cannot express a
        // clear through this DTO, and does not offer one; see the repository.
        val encoded = json.encodeToString(UpdateScheduleRequest(chatProjectId = null, name = "n"))

        assertEquals("""{"name":"n"}""", encoded)
    }
}
