package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.model.MEMORY_KEY_MAX_LENGTH
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.error_failed_to_load_memories
import com.garfiec.librechat.feature.settings.resources.memories_no_access_desc
import com.garfiec.librechat.feature.settings.resources.memory_error_agent
import com.garfiec.librechat.feature.settings.resources.memory_error_content_filtered
import com.garfiec.librechat.feature.settings.resources.memory_error_delete
import com.garfiec.librechat.feature.settings.resources.memory_error_forbidden
import com.garfiec.librechat.feature.settings.resources.memory_error_key_route_shadowed
import com.garfiec.librechat.feature.settings.resources.memory_error_network
import com.garfiec.librechat.feature.settings.resources.memory_error_not_found
import com.garfiec.librechat.feature.settings.resources.memory_error_not_found_readd
import com.garfiec.librechat.feature.settings.resources.memory_error_save
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle
import com.garfiec.librechat.feature.settings.resources.memory_error_toggle_forbidden
import com.garfiec.librechat.feature.settings.resources.memory_key_exists
import com.garfiec.librechat.feature.settings.resources.memory_key_invalid
import com.garfiec.librechat.feature.settings.resources.memory_key_reserved
import com.garfiec.librechat.feature.settings.resources.memory_key_too_long
import com.garfiec.librechat.feature.settings.viewmodel.MemoryFailure
import com.garfiec.librechat.feature.settings.viewmodel.MemoryOperation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The page's string for [failure]. [operation] words the generic case by what failed; null is the
 * list's load. A [MemoryFailure.ServerMessage] is shown as the server wrote it, never through this.
 */
internal fun memoryFailureResource(failure: MemoryFailure, operation: MemoryOperation?): StringResource {
    val isListRead = operation == null || operation == MemoryOperation.Refresh
    // The list route's 403 is about reading (USE + READ), not the "change" a write's 403 means.
    if (failure == MemoryFailure.Forbidden && isListRead) return Res.string.memories_no_access_desc
    // The preferences route's 403 is about OPT_OUT, which says nothing about editing memories.
    if (failure == MemoryFailure.Forbidden && operation is MemoryOperation.SetEnabled) {
        return Res.string.memory_error_toggle_forbidden
    }
    return memoryFailureResource(
        failure = failure,
        generic = when (operation) {
            null, MemoryOperation.Refresh -> Res.string.error_failed_to_load_memories
            is MemoryOperation.Create, is MemoryOperation.Update -> Res.string.memory_error_save
            is MemoryOperation.Delete -> Res.string.memory_error_delete
            is MemoryOperation.SetEnabled -> Res.string.memory_error_toggle
        },
    )
}

internal fun memoryFailureResource(failure: MemoryFailure, generic: StringResource): StringResource =
    when (failure) {
        MemoryFailure.DuplicateKey -> Res.string.memory_key_exists
        MemoryFailure.Forbidden -> Res.string.memory_error_forbidden
        MemoryFailure.ContentFiltered -> Res.string.memory_error_content_filtered
        MemoryFailure.AgentUnavailable -> Res.string.memory_error_agent
        MemoryFailure.Network -> Res.string.memory_error_network
        MemoryFailure.NotFound -> Res.string.memory_error_not_found
        MemoryFailure.KeyRouteShadowed -> Res.string.memory_error_key_route_shadowed
        is MemoryFailure.ServerMessage, MemoryFailure.Generic -> generic
    }

/** [memoryFailureResource] in a composition, e.g. the first-load error banner. */
@Composable
internal fun memoryFailureText(failure: MemoryFailure, operation: MemoryOperation?): String =
    if (failure is MemoryFailure.ServerMessage) failure.text else stringResource(memoryFailureResource(failure, operation))

/** A save's failure as the memory sheet shows it: worded as a save, whether it created or edited. */
@Composable
internal fun memorySaveFailureText(failure: MemoryFailure): String = when (failure) {
    is MemoryFailure.ServerMessage -> failure.text
    // The sheet has turned into an add of the same draft, so it says what Save now does.
    MemoryFailure.NotFound -> stringResource(Res.string.memory_error_not_found_readd)
    else -> stringResource(memoryFailureResource(failure, generic = Res.string.memory_error_save))
}

/** [memoryFailureResource] from a coroutine, e.g. a snackbar shown for an event. */
internal suspend fun memoryFailureMessage(failure: MemoryFailure, operation: MemoryOperation?): String =
    if (failure is MemoryFailure.ServerMessage) failure.text else getString(memoryFailureResource(failure, operation))

/**
 * The key field's error line, or null for none. A blank key is not reported: it is what disables
 * Save, and an error under the field the moment the sheet opens would scold an empty form.
 */
@Composable
internal fun memoryKeyErrorText(problem: MemoryKeyProblem?): String? = when (problem) {
    null, MemoryKeyProblem.REQUIRED -> null
    MemoryKeyProblem.TOO_LONG -> {
        val localeTag = LocalDateTimeFormat.current.localeTag
        val limit = remember(localeTag) { formatLocalizedNumber(MEMORY_KEY_MAX_LENGTH.toDouble(), localeTag) }
        stringResource(Res.string.memory_key_too_long, limit)
    }
    MemoryKeyProblem.PATTERN -> stringResource(Res.string.memory_key_invalid)
    MemoryKeyProblem.RESERVED -> stringResource(Res.string.memory_key_reserved)
    MemoryKeyProblem.DUPLICATE -> stringResource(Res.string.memory_key_exists)
}
