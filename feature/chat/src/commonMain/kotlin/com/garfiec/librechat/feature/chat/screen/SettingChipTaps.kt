package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.feature.chat.components.ephemeralToolMeta
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_undo
import com.garfiec.librechat.feature.chat.resources.setting_chip_set_by
import com.garfiec.librechat.feature.chat.resources.setting_chip_turned_off
import com.garfiec.librechat.feature.chat.resources.tool_set_by_spec
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipTap
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.util.tapFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * What a tap on a settings-line chip does ([SettingChips.tapFor]): a spec's own tool says who set it;
 * a pinned tool switches and stays; anything else switches off, leaves the line and offers Undo,
 * which switches it back on through the same toggle ([onTurnBackOn]). The toggles are the "+"
 * sheet's, so a global one (code, file search, memory, MCP) is off in every chat, as the snackbar
 * says, and a spec chat's additions are this chat's. An MCP server that needs sign-in signs in
 * instead ([onSignIn], with its stored name).
 *
 * The Undo belongs to the composer whose chip was tapped. [editingQueuedId] names it (the queued
 * item being edited, null for the new-message draft): when it changes, the queued-edit swap has
 * handed the line to another draft's tools, so the snackbar goes, and [onTurnBackOn] carries the
 * id the chip was turned off under for the ViewModel to check too.
 */
@Composable
internal fun rememberSettingChipTap(
    chips: SettingChips,
    editingQueuedId: String?,
    snackbarHostState: SnackbarHostState,
    onToggle: (SettingChip) -> Unit,
    onTurnBackOn: (chip: SettingChip, turnedOffWhileEditing: String?) -> Unit,
    onOpenThinking: () -> Unit,
    onSignIn: (serverName: String) -> Unit,
): (SettingChip) -> Unit {
    val scope = rememberCoroutineScope()
    val currentChips by rememberUpdatedState(chips)
    val currentEditingId by rememberUpdatedState(editingQueuedId)
    val pendingUndo = remember { PendingChipUndo() }
    LaunchedEffect(editingQueuedId) {
        // Cancelling the snackbar's coroutine takes the snackbar down with it.
        pendingUndo.job?.cancel()
        pendingUndo.job = null
    }
    val toggle by rememberUpdatedState(onToggle)
    val turnBackOn by rememberUpdatedState(onTurnBackOn)
    val openThinking by rememberUpdatedState(onOpenThinking)
    val signIn by rememberUpdatedState(onSignIn)
    return remember(scope, snackbarHostState) {
        { chip ->
            when (val tap = currentChips.tapFor(chip)) {
                SettingChipTap.OpenThinking -> openThinking()
                is SettingChipTap.SignIn -> signIn(tap.serverName)
                SettingChipTap.Toggle -> toggle(chip)
                is SettingChipTap.ExplainLock -> scope.launch {
                    val message = tap.specLabel?.let { getString(Res.string.setting_chip_set_by, it) }
                        ?: getString(Res.string.tool_set_by_spec)
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                SettingChipTap.TurnOffWithUndo -> {
                    val turnedOffWhileEditing = currentEditingId
                    toggle(chip)
                    pendingUndo.job = scope.launch {
                        val name = settingChipName(chip) ?: return@launch
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = getString(Res.string.setting_chip_turned_off, name),
                            actionLabel = getString(Res.string.action_undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) turnBackOn(chip, turnedOffWhileEditing)
                    }
                }
            }
        }
    }
}

/** The chip Undo snackbar's coroutine, so a composer swap can take it down. Read and written on the main thread. */
private class PendingChipUndo {
    var job: Job? = null
}

private suspend fun settingChipName(chip: SettingChip): String? = when (chip) {
    is SettingChip.Tool -> ephemeralToolMeta(chip.key)?.let { getString(it.titleRes) }
    is SettingChip.Mcp -> chip.label
    is SettingChip.Thinking -> null
}
