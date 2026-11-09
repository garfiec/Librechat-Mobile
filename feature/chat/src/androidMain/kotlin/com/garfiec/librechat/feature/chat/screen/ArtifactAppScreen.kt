package com.garfiec.librechat.feature.chat.screen

import android.app.ActivityManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.repository.ArtifactShortcutRepository
import com.garfiec.librechat.core.model.ArtifactShortcut
import com.garfiec.librechat.core.model.displayLabel
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.components.CodeBlock
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactLibraryGate
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactWebPage
import com.garfiec.librechat.feature.chat.components.artifact.isPreviewableType
import com.garfiec.librechat.feature.chat.components.artifact.renderIcon
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.artifact_shortcut_back
import com.garfiec.librechat.feature.chat.resources.artifact_shortcut_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed interface ArtifactLoad {
    data object Loading : ArtifactLoad
    data class Loaded(val shortcut: ArtifactShortcut) : ArtifactLoad
    data object NotFound : ArtifactLoad
}

/**
 * A home-screen-pinned artifact as an app of its own: the page fills the screen with no viewer
 * chrome, Back walks the page's history, and the Recents card takes the pin's label and icon. Loads the Room
 * snapshot by [snapshotId], so it opens logged out too; [onClose] leaves the app.
 */
@Composable
fun ArtifactAppScreen(
    snapshotId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<ArtifactShortcutRepository>()
    val activity = LocalActivity.current
    var state by remember(snapshotId) { mutableStateOf<ArtifactLoad>(ArtifactLoad.Loading) }

    LaunchedEffect(snapshotId) {
        val snapshot = repository.get(snapshotId)
        state = snapshot?.let { ArtifactLoad.Loaded(it) } ?: ArtifactLoad.NotFound
        if (snapshot != null && activity != null) {
            val icon = withContext(Dispatchers.Default) { renderIcon(snapshot) }
            // The Bitmap constructor is the only one that takes a runtime-drawn icon.
            @Suppress("DEPRECATION")
            activity.setTaskDescription(ActivityManager.TaskDescription(snapshot.displayLabel, icon))
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val contentModifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        when (val current = state) {
            ArtifactLoad.Loading -> LoadingIndicator(modifier = contentModifier)
            is ArtifactLoad.Loaded -> ArtifactAppContent(current.shortcut, contentModifier)
            ArtifactLoad.NotFound -> Column(
                modifier = contentModifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = stringResource(Res.string.artifact_shortcut_unavailable),
                    modifier = Modifier.weight(1f),
                )
                AdaptiveTextButton(onClick = onClose) {
                    Text(stringResource(Res.string.artifact_shortcut_back))
                }
            }
        }
    }
}

@Composable
private fun ArtifactAppContent(shortcut: ArtifactShortcut, modifier: Modifier = Modifier) {
    if (isPreviewableType(shortcut.type)) {
        val isDarkTheme = isSurfaceDark()
        ArtifactLibraryGate(
            content = shortcut.content,
            type = shortcut.type,
            modifier = modifier,
            fillHeight = true,
        ) { allowed, previewModifier ->
            ArtifactWebPage(
                content = shortcut.content,
                type = shortcut.type,
                isDarkTheme = isDarkTheme,
                modifier = previewModifier,
                allowedLibraries = allowed,
                backThroughHistory = true,
            )
        }
    } else {
        Box(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CodeBlock(code = shortcut.content, language = shortcut.language, modifier = Modifier.fillMaxWidth())
        }
    }
}
