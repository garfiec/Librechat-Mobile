package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.repository.ArtifactShortcutRepository
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.artifact_shortcuts_disabled_message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Lists the artifacts the user pinned to the home screen and lets them remove one. Delete removes the
 * local snapshot and disables the launcher shortcut; since Android can't remove an already-placed icon,
 * a lingering icon becomes inert (tapping it shows the disabled message). The list reflects the app's
 * snapshots, not the launcher's exact set.
 */
@Composable
fun ArtifactShortcutsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<ArtifactShortcutRepository>()
    val shortcuts by repository.observeAll().collectAsStateWithLifecycle(emptyList())
    val disableShortcut = rememberDisableHomeScreenShortcut()
    val disabledMessage = stringResource(Res.string.artifact_shortcuts_disabled_message)
    val scope = rememberCoroutineScope()

    ArtifactShortcutsContent(
        shortcuts = shortcuts,
        onDelete = { shortcut ->
            // Disable the launcher icon first (best-effort), then drop the snapshot —
            // both off the main thread (disableShortcut is a launcher binder IPC).
            scope.launch {
                withContext(Dispatchers.Default) { disableShortcut(shortcut.id, disabledMessage) }
                repository.delete(shortcut.id)
            }
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
