package com.garfiec.librechat.core.model.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The second absent-means-OFF interface flag — with a DIFFERENT rule from the first.
 *
 * `interface.schedules` and `interface.traceViewer` look alike and are not: schedules is a
 * boolean-or-object union where `{}` means ON, while traceViewer is an object-only section whose
 * `enabled` must be explicitly `true`, so `{}` means OFF. Reusing one resolver for the other is
 * the mistake these cases exist to catch, and it would compile.
 */
class TraceViewerEnabledTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun config(body: String): InterfaceConfig = json.decodeFromString(body)

    @Test
    fun an_absent_section_is_off() {
        assertFalse(isTraceViewerEnabled(config("{}").traceViewer))
        assertFalse(isTraceViewerEnabled(config("""{"traceViewer": null}""").traceViewer))
    }

    @Test
    fun an_empty_object_is_off_unlike_schedules() {
        // THE divergence. `interface.schedules: {}` is ON; this is not.
        assertFalse(isTraceViewerEnabled(config("""{"traceViewer": {}}""").traceViewer))
        assertTrue(isSchedulesEnabled(config("""{"schedules": {}}""").schedules))
    }

    @Test
    fun only_an_explicit_enabled_true_turns_it_on() {
        assertTrue(isTraceViewerEnabled(config("""{"traceViewer": {"enabled": true}}""").traceViewer))
        assertFalse(isTraceViewerEnabled(config("""{"traceViewer": {"enabled": false}}""").traceViewer))
    }

    @Test
    fun the_other_fields_do_not_enable_it_on_their_own() {
        // A deployment that tuned the budgets but never said `enabled` has not turned it on.
        assertFalse(
            isTraceViewerEnabled(
                config("""{"traceViewer": {"maxRecords": 500, "showInputOutput": true}}""").traceViewer,
            ),
        )
    }

    @Test
    fun the_boolean_form_is_off_because_it_is_not_a_union() {
        // `traceViewerSchema` is `z.object({…}).optional()` with no boolean arm, and upstream
        // reading `.enabled` off a boolean gets undefined. A deployment that wrote `true` by
        // analogy with `schedules` gets the same nothing from both clients.
        assertFalse(isTraceViewerEnabled(config("""{"traceViewer": true}""").traceViewer))
    }

    @Test
    fun an_enabled_section_still_carries_its_server_side_budgets() {
        // Kept as raw JSON: `showInputOutput` and the four numeric limits are enforced entirely
        // server-side, so nothing here reads them and nothing here mirrors them.
        val config = config(
            """{"traceViewer": {"enabled": true, "showInputOutput": false, "maxRecords": 250}}""",
        )

        assertTrue(isTraceViewerEnabled(config.traceViewer))
    }
}
