package com.garfiec.librechat.feature.skills.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.skills.SkillNameIssue
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skills_retry
import com.garfiec.librechat.feature.skills.skillCategoryLabel
import com.garfiec.librechat.feature.skills.text
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorUiState
import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SkillEditorScreen(
    skillId: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillEditorViewModel = koinViewModel { parametersOf(skillId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnBack by rememberUpdatedState(onBack)
    val nameField = remember { NameFieldTarget() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SkillEditorEvent.Saved -> currentOnSave(event.skillId)
                SkillEditorEvent.Exit -> currentOnBack()
                // Save is in the top bar and the Name field at the top of a long form: without this
                // a refused save looks like nothing happened.
                SkillEditorEvent.NameTaken -> nameField.reveal()
                // The same for any other refusal: its reason (or the conflict banner) sits at the top.
                SkillEditorEvent.SaveFailed -> scrollState.animateScrollTo(0)
            }
        }
    }

    // Only while dirty or saving, so a clean form keeps the predictive-back preview.
    PlatformBackHandler(enabled = uiState.hasUnsavedChanges || uiState.isSaving, onBack = viewModel::onBackRequested)
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(
            onDiscard = viewModel::discardChanges,
            onDismiss = viewModel::dismissDiscardConfirmation,
        )
    }

    SkillEditorScreenContent(
        state = uiState,
        actions = SkillEditorActions(
            onBack = viewModel::onBackRequested,
            onSave = viewModel::save,
            onNameChange = viewModel::onNameChanged,
            onDisplayTitleChange = viewModel::onDisplayTitleChanged,
            onDescriptionChange = viewModel::onDescriptionChanged,
            onBodyChange = viewModel::onBodyChanged,
            onCategoryChange = viewModel::onCategoryChanged,
            onAlwaysApplyChange = viewModel::onAlwaysApplyChanged,
            onDismissConflict = viewModel::dismissConflictNotice,
            onRetryLoad = viewModel::retryLoad,
        ),
        modifier = modifier,
        nameField = nameField,
        scrollState = scrollState,
    )
}

/** Lets the page scroll the Name field into view and focus it when a save is refused over it. */
internal class NameFieldTarget {
    val bringIntoView = BringIntoViewRequester()
    val focus = FocusRequester()

    suspend fun reveal() {
        bringIntoView.bringIntoView()
        focus.requestFocus()
    }
}

/** What the editor can ask for. */
internal class SkillEditorActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onDisplayTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onBodyChange: (String) -> Unit,
    val onCategoryChange: (String) -> Unit,
    val onAlwaysApplyChange: (Boolean) -> Unit,
    val onDismissConflict: () -> Unit,
    val onRetryLoad: () -> Unit,
)

/** The editor without its ViewModel. */
@Composable
internal fun SkillEditorScreenContent(
    state: SkillEditorUiState,
    actions: SkillEditorActions,
    modifier: Modifier = Modifier,
    nameField: NameFieldTarget = remember { NameFieldTarget() },
    scrollState: ScrollState = rememberScrollState(),
) {
    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.skills_back), actions.onBack),
                    title = BarTitle(
                        stringResource(
                            if (state.isEditMode) Res.string.skill_editor_edit_title else Res.string.skill_editor_create_title,
                        ),
                    ),
                    actions = listOf(
                        BarAction.Text(
                            id = "save",
                            label = stringResource(Res.string.skill_save),
                            compactIcon = BarIcons.Confirm,
                            enabled = state.canSave,
                            onClick = actions.onSave,
                        ),
                    ),
                ),
            )
        },
    ) { padding ->
        if (state.loadFailed) {
            // No form over a skill that never loaded: there is nothing to edit, and no version to save against.
            ErrorBanner(
                message = state.error?.text().orEmpty(),
                onRetry = actions.onRetryLoad,
                retryLabel = stringResource(Res.string.skills_retry),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@AdaptiveScaffold
        }
        if (state.isLoading || state.loadPending) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator()
            }
            return@AdaptiveScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.showConflictNotice) {
                ConflictBanner(onDismiss = actions.onDismissConflict)
            }
            state.error?.let { err ->
                // Announced: the user tapped Save in the bar, not anything near this text.
                Text(
                    err.text(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            NameField(state, actions.onNameChange, nameField)
            AdaptiveOutlinedTextField(
                value = state.displayTitle,
                onValueChange = actions.onDisplayTitleChange,
                readOnly = state.isSaving,
                label = { Text(stringResource(Res.string.skill_field_display_title)) },
                singleLine = true,
                isError = state.displayTitleTooLong,
                supportingText = if (state.displayTitleTooLong) {
                    { Text(stringResource(Res.string.skill_field_display_title_error)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AdaptiveOutlinedTextField(
                value = state.description,
                onValueChange = actions.onDescriptionChange,
                readOnly = state.isSaving,
                label = { Text(stringResource(Res.string.skill_field_description)) },
                isError = state.descriptionTooLong,
                // Also while empty, uncoloured, once the field or a valid name makes it the reason Save waits.
                supportingText = if (state.descriptionTooLong || state.showDescriptionRequired) {
                    { Text(stringResource(Res.string.skill_field_description_error)) }
                } else {
                    null
                },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryPicker(
                selected = state.category,
                categories = state.availableCategories,
                onSelect = actions.onCategoryChange,
                enabled = !state.isSaving,
            )
            // One toggleable row, as on the detail page: the switch takes its name from the label,
            // and the label is part of the touch target.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = state.alwaysApply,
                        enabled = !state.isSaving,
                        role = Role.Switch,
                        onValueChange = actions.onAlwaysApplyChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.skill_field_always_apply),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                )
                AdaptiveSwitch(checked = state.alwaysApply, onCheckedChange = null, enabled = !state.isSaving)
            }
            AdaptiveOutlinedTextField(
                value = state.body,
                onValueChange = actions.onBodyChange,
                readOnly = state.isSaving,
                label = { Text(stringResource(Res.string.skill_field_body)) },
                isError = state.bodyTooLong,
                supportingText = if (state.bodyTooLong) {
                    { Text(stringResource(Res.string.skill_field_body_error)) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            )
        }
    }
}

@Composable
private fun NameField(state: SkillEditorUiState, onNameChange: (String) -> Unit, target: NameFieldTarget) {
    val issue = state.nameIssue
    val supporting: String? = when {
        issue == SkillNameIssue.INVALID_FORMAT -> stringResource(Res.string.skill_field_name_error)
        issue == SkillNameIssue.RESERVED_PREFIX -> stringResource(Res.string.skill_field_name_reserved_prefix)
        issue == SkillNameIssue.RESERVED_WORD -> stringResource(Res.string.skill_field_name_reserved_word, state.name)
        state.nameTaken -> stringResource(Res.string.skill_field_name_taken)
        else -> null
    }
    AdaptiveOutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        readOnly = state.isSaving,
        label = { Text(stringResource(Res.string.skill_field_name)) },
        placeholder = { Text(stringResource(Res.string.skill_field_name_hint)) },
        singleLine = true,
        // The name is [a-z0-9-] only: a committed suggestion's trailing space would fail validation.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        ),
        isError = supporting != null,
        supportingText = supporting?.let { text ->
            {
                // A refused save's reason is announced, not just drawn: the user tapped Save, not this field.
                val announce = if (issue == null && state.nameTaken) {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                } else {
                    Modifier
                }
                Text(text, announce)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(target.bringIntoView)
            .focusRequester(target.focus),
    )
}

/**
 * Category picker: a preset menu sourced from `GET /api/categories` (the same shared list Prompts
 * use), matching web's CategorySelector. [selected] is the persisted category `value`; the field
 * and the menu show each value's label. A saved category that isn't in the preset list
 * (custom/legacy) is still offered, so an edit never silently drops it.
 */
@Composable
private fun CategoryPicker(
    selected: String,
    categories: List<Category>,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val values = remember(categories) { categories.mapNotNull { it.value?.takeIf { v -> v.isNotBlank() } } }
    val noneLabel = stringResource(Res.string.skill_category_none)
    val fieldLabel = stringResource(Res.string.skill_field_category)
    val shown = if (selected.isBlank()) noneLabel else skillCategoryLabel(selected, categories)

    Box(modifier.fillMaxWidth()) {
        AdaptiveOutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(fieldLabel) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            // The cover below speaks for the field.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        // A read-only field still takes the tap for focus; this cover takes it for the menu instead.
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = fieldLabel
                    stateDescription = shown
                }
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true },
        )
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CategoryMenuItem(noneLabel, isSelected = selected.isBlank()) {
                // "None" clears the category (sent as null, or "" on an edit that had one).
                onSelect("")
                expanded = false
            }
            values.forEach { value ->
                CategoryMenuItem(skillCategoryLabel(value, categories), isSelected = value == selected) {
                    onSelect(value)
                    expanded = false
                }
            }
            if (selected.isNotBlank() && selected !in values) {
                CategoryMenuItem(shown, isSelected = true) { expanded = false }
            }
        }
    }
}

@Composable
private fun CategoryMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = if (isSelected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}

@Composable
private fun ConflictBanner(onDismiss: () -> Unit) {
    AdaptiveCard(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = stringResource(Res.string.skill_editor_conflict),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            AdaptiveOutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.skill_conflict_dismiss))
            }
        }
    }
}
