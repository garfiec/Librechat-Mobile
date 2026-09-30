package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.StringResource

/**
 * MIRRORED from upstream `Parts/guidance.ts` (v0.8.8). The poll tool's notes, messages and notices
 * are model-facing English, matched EXACTLY here so the user sees a localized sentence instead;
 * anything unrecognized points at the raw details rather than surfacing the prose.
 */
internal object BackgroundTaskGuidance {

    private val NOTES: Map<String, StringResource> = mapOf(
        "Generated files were saved and attached to the tool call that dispatched this task." to
            Res.string.background_tasks_files_attached,
        "Output and any generated files are being attached to the tool call that dispatched this task." to
            Res.string.background_tasks_files_attaching,
        "The tool produced an artifact that is not included inline." to
            Res.string.background_tasks_artifact_not_inline,
        "Still running outside this turn; its result will arrive as a new turn when it finishes." to
            Res.string.background_tasks_running_elsewhere,
        "Finished, but its result has not been delivered; it will arrive as a new turn unless you poll or cancel it." to
            Res.string.background_tasks_pending_delivery_note,
        "Automatic delivery failed; this result will not arrive as a new turn. Poll it to collect the result." to
            Res.string.background_tasks_failed_delivery_note,
    )

    private val MESSAGES: Map<String, StringResource> = mapOf(
        "Cancellation was requested. The task remains active until its executor settles; " +
            "poll again for a terminal result." to Res.string.background_tasks_cancellation_requested,
    )

    private val NOTICES: Map<String, StringResource> = mapOf(
        "invalid" to Res.string.background_tasks_notice_invalid,
        "rejected" to Res.string.background_tasks_notice_rejected,
        "unavailable" to Res.string.background_tasks_notice_unavailable,
        "not_found" to Res.string.background_tasks_notice_not_found,
        "outcome_unknown" to Res.string.background_tasks_notice_outcome_unknown,
        "result_unavailable" to Res.string.background_tasks_notice_result_unavailable,
        "result_persisting" to Res.string.background_tasks_notice_result_persisting,
        "delivery_scheduled" to Res.string.background_tasks_notice_delivery_scheduled,
        "cancelled" to Res.string.background_tasks_result_discarded,
        "error" to Res.string.background_tasks_notice_error,
    )

    fun noteKey(note: String): StringResource = NOTES[note] ?: Res.string.background_tasks_more_in_raw_details

    fun messageKey(message: String): StringResource =
        MESSAGES[message] ?: Res.string.background_tasks_more_in_raw_details

    fun noticeKey(status: String, message: String): StringResource = when {
        status == "invalid" && message == "Cancellation is not enabled for ordinary background tools." ->
            Res.string.background_tasks_cancel_disabled
        status == "invalid" && message == "This control action is supported only for subagent tasks." ->
            Res.string.background_tasks_notice_subagent_only
        status == "unavailable" && message.startsWith("The pending result could not be discarded right now.") ->
            Res.string.background_tasks_notice_discard_unavailable
        else -> NOTICES[status] ?: Res.string.background_tasks_more_in_raw_details
    }
}
