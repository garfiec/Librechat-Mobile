package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * What a tap on a settings-line chip does ([SettingChips.tapFor]): a spec's own tool says who set it;
 * a pinned tool switches and stays; anything else switches off, leaves the line and offers Undo,
 * which switches it back on through the same toggle ([onTurnBackOn]). The toggles are the "+"
 * sheet's, so a global one (code, file search, memory, MCP) is off in every chat, as the snackbar
 * says, and a spec chat's additions are this chat's.
 */
@Composable
internal fun rememberSettingChipTap(
    chips: SettingChips,
    snackbarHostState: SnackbarHostState,
    onToggle: (SettingChip) -> Unit,
    onTurnBackOn: (SettingChip) -> Unit,
    onOpenThinking: () -> Unit,
): (SettingChip) -> Unit {
    val scope = rememberCoroutineScope()
    val currentChips by rememberUpdatedState(chips)
    val toggle by rememberUpdatedState(onToggle)
    val turnBackOn by rememberUpdatedState(onTurnBackOn)
    val openThinking by rememberUpdatedState(onOpenThinking)
    return remember(scope, snackbarHostState) {
        { chip ->
            when (val tap = currentChips.tapFor(chip)) {
                SettingChipTap.OpenThinking -> openThinking()
                SettingChipTap.Toggle -> toggle(chip)
                is SettingChipTap.ExplainLock -> scope.launch {
                    val message = tap.specLabel?.let { getString(Res.string.setting_chip_set_by, it) }
                        ?: getString(Res.string.tool_set_by_spec)
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
                SettingChipTap.TurnOffWithUndo -> {
                    toggle(chip)
                    scope.launch {
                        val name = settingChipName(chip) ?: return@launch
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = getString(Res.string.setting_chip_turned_off, name),
                            actionLabel = getString(Res.string.action_undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) turnBackOn(chip)
                    }
                }
            }
        }
    }
}

private suspend fun settingChipName(chip: SettingChip): String? = when (chip) {
    is SettingChip.Tool -> ephemeralToolMeta(chip.key)?.let { getString(it.titleRes) }
    is SettingChip.Mcp -> chip.label
    is SettingChip.Thinking -> null
}
