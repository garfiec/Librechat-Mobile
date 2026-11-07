package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChoice
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirement
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceUiState

@PreviewNarrowScreens
@Composable
private fun CodeWorkspaceFoldedMaterialPreview() = WorkspaceSurface(UiStyle.MATERIAL) { FoldedStates() }

@PreviewNarrowScreens
@Composable
private fun CodeWorkspaceFoldedGlassPreview() = WorkspaceSurface(UiStyle.LIQUID_GLASS) { FoldedStates() }

@PreviewNarrowScreens
@Composable
private fun CodeWorkspaceNeedsYouMaterialPreview() = WorkspaceSurface(UiStyle.MATERIAL) { NeedsYouStates() }

@PreviewNarrowScreens
@Composable
private fun CodeWorkspaceNeedsYouGlassPreview() = WorkspaceSurface(UiStyle.LIQUID_GLASS) { NeedsYouStates() }

@Composable
private fun WorkspaceSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            Text(
                text = "Run the core:data tests and fix what fails, then open a draft. ".repeat(6),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

@Composable
private fun FoldedStates() {
    // A healthy saved chat on its machine, with tools on the same line.
    ComposerHost(
        healthy.copy(approval = approval),
        SettingChips(
            listOf(
                SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.ON, pinned = true),
                SettingChip.Tool(ToolConstants.FILE_SEARCH, SettingChipState.PINNED_OFF, pinned = true),
            ),
        ),
    )
    // Long names, Detach on offer (the chip opens the transition sheet), nothing else on the line.
    ComposerHost(
        healthy.copy(
            machine = CodeMachineChip("garfie-macbook-pro-16-office", emptyList()),
            workspace = CodeWorkspaceChip(CodeWorkspaceChipLabel.NAME, name = "Librechat-Mobile-release-2026.11", opensSheet = true),
        ),
        SettingChips.EMPTY,
    )
    // Several machines: the count alone.
    ComposerHost(
        healthy.copy(
            machine = null,
            workspace = CodeWorkspaceChip(CodeWorkspaceChipLabel.COUNT, count = 2, opensSheet = false, refreshes = true),
            approval = approval,
        ),
        SettingChips.EMPTY,
    )
    // No workspace shown, only the approval mode.
    ComposerHost(CodeWorkspaceUiState(approval = approval), SettingChips.EMPTY)
}

@Composable
private fun NeedsYouStates() {
    // Choosing: Send waits, and the requirement line says why.
    ComposerHost(
        CodeWorkspaceUiState(
            visible = true,
            holdsSend = true,
            machine = CodeMachineChip(
                name = "studio-mac",
                choices = listOf(CodeMachineChoice("studio", "studio-mac", true), CodeMachineChoice("vm", "build-vm", false)),
            ),
            workspace = CodeWorkspaceChip(CodeWorkspaceChipLabel.CHOOSE),
            requirements = listOf(CodeRequirement("Switchboard Dev", "studio-mac", CodeRequirementState.CHOOSE)),
            approval = approval,
        ),
        SettingChips(listOf(SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.ON, pinned = true))),
    )
    // Sealed without a workspace: the chip's tap explains what that means.
    ComposerHost(
        CodeWorkspaceUiState(
            visible = true,
            locked = true,
            workspace = CodeWorkspaceChip(CodeWorkspaceChipLabel.NONE, opensSheet = false, refreshes = false),
            footnote = CodeFootnote.WITHOUT_WORKSPACE,
        ),
        SettingChips.EMPTY,
    )
}

@Composable
private fun ComposerHost(state: CodeWorkspaceUiState, chips: SettingChips) {
    CompositionLocalProvider(LocalCodeWorkspaceComposer provides composer(state)) {
        val codeWorkspace = LocalCodeWorkspaceComposer.current
        Column {
            ComposerSettingsLine(
                chips = chips,
                onChipTap = {},
                leading = codeWorkspaceLineLeading(codeWorkspace),
            )
            codeWorkspace?.let { CodeWorkspaceNotice(it) }
        }
    }
}

private val approval = CodeApprovalChip(
    CodeApprovalOption.ACCEPT_EDITS,
    listOf(CodeApprovalOption.ASK, CodeApprovalOption.ACCEPT_EDITS, CodeApprovalOption.FULL_ACCESS),
)

private val healthy = CodeWorkspaceUiState(
    folded = true,
    locked = true,
    machine = CodeMachineChip("studio-mac", emptyList()),
    workspace = CodeWorkspaceChip(CodeWorkspaceChipLabel.NAME, name = "Librechat-Mobile", opensSheet = false, refreshes = true),
    branch = "develop",
    checkout = CodeCheckoutChip(CodeCheckoutMode.AUTO, pickable = false),
)

private fun composer(state: CodeWorkspaceUiState) = CodeWorkspaceComposer(
    state = state,
    onOpenSheet = {},
    onDismissSheet = {},
    onSelectWorkspace = { _, _ -> },
    onSetCheckout = { _, _ -> },
    onSelectNoWorkspace = {},
    onRefresh = {},
)
