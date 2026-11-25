package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.background_tasks_more_in_raw_details
import com.garfiec.librechat.feature.chat.resources.background_tasks_subagent_poll_guidance
import com.garfiec.librechat.feature.chat.resources.background_tasks_subagent_wakeup_guidance
import kotlin.test.Test
import kotlin.test.assertEquals

/** The subagent poll guidance sentences (upstream `guidance.ts` MESSAGE_KEYS, v0.8.9-rc1), matched verbatim. */
class BackgroundTaskGuidanceTest {

    @Test
    fun the_subagent_delivery_sentences_map_to_their_guidance() {
        assertEquals(
            Res.string.background_tasks_subagent_wakeup_guidance,
            BackgroundTaskGuidance.messageKey(
                "This subagent resumes you automatically when it finishes, so there is no need to check again. " +
                    "If you do, wait next_check_s and pass since: activity.cursor.",
            ),
        )
        assertEquals(
            Res.string.background_tasks_subagent_wakeup_guidance,
            BackgroundTaskGuidance.messageKey(
                "Automatic completion delivery is enabled for this subagent task. Continue independent work if " +
                    "available; otherwise end this turn and the host will resume you when the task finishes. Do " +
                    "not repeatedly poll an unchanged running task. Use check_background_task only for explicit " +
                    "status or control, or as a fallback if automatic delivery is unavailable.",
            ),
        )
        assertEquals(
            Res.string.background_tasks_subagent_poll_guidance,
            BackgroundTaskGuidance.messageKey(
                "Results are not pushed for this subagent. Check again after next_check_s, passing since: " +
                    "activity.cursor to get only new activity.",
            ),
        )
    }

    @Test
    fun a_reworded_sentence_points_at_the_raw_details() {
        assertEquals(
            Res.string.background_tasks_more_in_raw_details,
            BackgroundTaskGuidance.messageKey("Results are not pushed for this subagent."),
        )
    }
}
