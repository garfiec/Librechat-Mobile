package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.feature.chat.components.lockedByMessage
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipTap
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.util.ToolsSheetFocus
import com.garfiec.librechat.feature.chat.util.tapFor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
internal fun rememberSettingChipTap(
    chips: SettingChips,
    snackbarHostState: SnackbarHostState,
    onToggle: (SettingChip) -> Unit,
    onShowInSheet: (ToolsSheetFocus) -> Unit,
    onOpenThinking: () -> Unit,
    onSignIn: (serverName: String) -> Unit,
): (SettingChip) -> Unit {
    val scope = rememberCoroutineScope()
    val currentChips by rememberUpdatedState(chips)
    val toggle by rememberUpdatedState(onToggle)
    val showInSheet by rememberUpdatedState(onShowInSheet)
    val openThinking by rememberUpdatedState(onOpenThinking)
    val signIn by rememberUpdatedState(onSignIn)
    return remember(scope, snackbarHostState) {
        { chip ->
            when (val tap = currentChips.tapFor(chip)) {
                SettingChipTap.OpenThinking -> openThinking()
                is SettingChipTap.SignIn -> signIn(tap.serverName)
                // The chip opens its own menu; its items come back through the composer chrome.
                SettingChipTap.SignInMenu -> Unit
                // A reconnect or cancel is out; the chip is disabled until it ends.
                SettingChipTap.WaitForSignIn -> Unit
                SettingChipTap.TurnOn -> toggle(chip)
                is SettingChipTap.ShowInSheet -> showInSheet(tap.focus)
                is SettingChipTap.ExplainLock -> scope.launch {
                    snackbarHostState.showNotice(getString(lockedByMessage(tap.specLabel), tap.specLabel.orEmpty()))
                }
            }
        }
    }
}
