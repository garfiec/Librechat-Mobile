package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_file_binary
import com.garfiec.librechat.feature.skills.resources.skill_file_conflict
import com.garfiec.librechat.feature.skills.resources.skill_file_image_description
import com.garfiec.librechat.feature.skills.resources.skill_file_load_failed
import com.garfiec.librechat.feature.skills.resources.skill_file_note_access_unknown
import com.garfiec.librechat.feature.skills.resources.skill_file_note_no_access
import com.garfiec.librechat.feature.skills.resources.skill_file_note_no_revision
import com.garfiec.librechat.feature.skills.resources.skill_file_note_skill_body
import com.garfiec.librechat.feature.skills.resources.skill_file_note_too_large
import com.garfiec.librechat.feature.skills.resources.skill_file_note_truncated
import com.garfiec.librechat.feature.skills.resources.skill_file_reload
import com.garfiec.librechat.feature.skills.resources.skill_file_save
import com.garfiec.librechat.feature.skills.resources.skill_file_save_failed
import com.garfiec.librechat.feature.skills.resources.skills_back
import com.garfiec.librechat.feature.skills.resources.skills_more_options
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileImage
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileKind
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileReadOnlyReason
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileUiState
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One skill file, full screen: text in a monospace editor with a top-bar Save, an image preview, or
 * a note for a file that is neither. Leaving with unsaved edits asks first, through system back and
 * the top bar's back alike (iOS has no system back to catch).
 */
@Composable
fun SkillFileScreen(
    skillId: String,
    relativePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillFileViewModel = koinViewModel { parametersOf(skillId, relativePath) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.saved, uiState.exitRequested) {
        if (uiState.saved || uiState.exitRequested) currentOnBack()
    }
    // Only while there's something to lose or a save to wait for, so a clean file keeps the
    // predictive-back preview. A landed save's pop is already on the way.
    PlatformBackHandler(
        enabled = uiState.isDirty || uiState.isSaving || uiState.saved,
        onBack = viewModel::onBackRequested,
    )
    if (uiState.discardPrompt != null) {
        DiscardChangesDialog(onDiscard = viewModel::confirmDiscard, onDismiss = viewModel::dismissDiscard)
    }

    SkillFileScreenContent(
        state = uiState,
        actions = SkillFileActions(
            onBack = viewModel::onBackRequested,
            onSave = viewModel::save,
            onReload = viewModel::reload,
            onDraftChange = viewModel::onDraftChange,
            onImageFailed = viewModel::onImageFailed,
        ),
        modifier = modifier,
    )
}

/** What the file screen can ask for. */
internal class SkillFileActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onReload: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onImageFailed: () -> Unit,
)

/**
 * The screen without its ViewModel. [image] draws a decoded preview; previews swap it, since an
 * image loader has nothing to decode there.
 */
@Composable
internal fun SkillFileScreenContent(
    state: SkillFileUiState,
    actions: SkillFileActions,
    modifier: Modifier = Modifier,
    image: @Composable (SkillFileImage, String, Modifier) -> Unit = { file, description, imageModifier ->
        SkillFileImageView(file, description, actions.onImageFailed, imageModifier)
    },
) {
    AdaptiveScaffold(
        // The editor ends where the keyboard begins, so the caret's line stays in view.
        modifier = modifier.imePadding(),
        topBar = { AdaptiveTopBar(spec = fileTopBarSpec(state, actions)) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SkillFileBanners(state, actions)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                SkillFileBody(state, actions, image)
            }
        }
    }
}

@Composable
private fun fileTopBarSpec(state: SkillFileUiState, actions: SkillFileActions): AdaptiveTopBarSpec {
    val actionList = buildList {
        // Offered wherever the file could be saved; disabled while it's clean, saving or in conflict.
        if (state.kind == SkillFileKind.TEXT && state.canEditFiles && state.readOnlyReason == null) {
            add(
                BarAction.Text(
                    id = "save",
                    label = stringResource(Res.string.skill_file_save),
                    compactIcon = BarIcons.Confirm,
                    enabled = state.canSave,
                    onClick = actions.onSave,
                ),
            )
        }
        add(
            BarAction.Menu(
                id = "more",
                icon = BarIcons.More,
                label = stringResource(Res.string.skills_more_options),
                sections = listOf(
                    BarMenuSection(
                        listOf(
                            BarMenuItem(
                                id = "reload",
                                label = stringResource(Res.string.skill_file_reload),
                                icon = BarIcons.History,
                                onClick = actions.onReload,
                                enabled = !state.isSaving,
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
    return AdaptiveTopBarSpec(
        navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), actions.onBack),
        // The leaf, not the path: one line cut at the end would show only the folder.
        title = BarTitle(state.relativePath.substringAfterLast('/'), maxLines = 1),
        actions = actionList,
    )
}

@Composable
private fun SkillFileBanners(state: SkillFileUiState, actions: SkillFileActions) {
    val bannerModifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
    // Above the editor, which can take the whole viewport.
    Column(Modifier.fillMaxWidth()) {
        if (state.conflict) {
            ErrorBanner(
                message = stringResource(Res.string.skill_file_conflict),
                onRetry = actions.onReload,
                retryLabel = stringResource(Res.string.skill_file_reload),
                modifier = bannerModifier,
            )
        }
        if (state.saveFailed) {
            ErrorBanner(
                message = state.saveError ?: stringResource(Res.string.skill_file_save_failed),
                modifier = bannerModifier,
            )
        }
        state.readOnlyReason?.let { reason ->
            Text(
                text = stringResource(readOnlyNote(reason)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = bannerModifier,
            )
        }
    }
}

private fun readOnlyNote(reason: SkillFileReadOnlyReason) = when (reason) {
    SkillFileReadOnlyReason.NO_ACCESS -> Res.string.skill_file_note_no_access
    SkillFileReadOnlyReason.ACCESS_UNKNOWN -> Res.string.skill_file_note_access_unknown
    SkillFileReadOnlyReason.SKILL_BODY -> Res.string.skill_file_note_skill_body
    SkillFileReadOnlyReason.TOO_LARGE -> Res.string.skill_file_note_too_large
    SkillFileReadOnlyReason.TRUNCATED -> Res.string.skill_file_note_truncated
    SkillFileReadOnlyReason.NO_REVISION -> Res.string.skill_file_note_no_revision
}

@Composable
private fun SkillFileBody(
    state: SkillFileUiState,
    actions: SkillFileActions,
    image: @Composable (SkillFileImage, String, Modifier) -> Unit,
) {
    val fillModifier = Modifier.fillMaxSize().padding(16.dp)
    when {
        state.loadFailed -> ErrorBanner(
            message = state.loadError ?: stringResource(Res.string.skill_file_load_failed),
            onRetry = actions.onReload,
            retryLabel = stringResource(Res.string.skills_retry),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        state.kind == SkillFileKind.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AdaptiveCircularProgressIndicator()
        }
        state.kind == SkillFileKind.TEXT -> AdaptiveOutlinedTextField(
            value = state.draft,
            onValueChange = actions.onDraftChange,
            readOnly = !state.isEditable || state.isSaving,
            // Names the field for a screen reader, which otherwise hears only "Edit box".
            label = { Text(state.relativePath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = fillModifier,
        )
        state.kind == SkillFileKind.IMAGE -> state.image?.let { file ->
            image(file, stringResource(Res.string.skill_file_image_description, state.relativePath), fillModifier)
        }
        else -> SkillFileBinaryNote(state, Modifier.fillMaxSize().padding(24.dp))
    }
}

/** The image the server serves inline, decoded by the app's image loader; a failure falls back to the note. */
@Composable
private fun SkillFileImageView(
    file: SkillFileImage,
    description: String,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = file.bytes,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        onError = { onError() },
        modifier = modifier,
    )
}

@Composable
private fun SkillFileBinaryNote(state: SkillFileUiState, modifier: Modifier = Modifier) {
    val name = state.filename ?: state.relativePath.substringAfterLast('/')
    val kind = fileKindOf(name, state.mimeType)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        FileTypeTile(kind = kind, size = 56.dp)
        Text(
            text = fileTypeSubtitle(kind, name, state.sizeBytes.takeIf { it > 0 }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.skill_file_binary),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
