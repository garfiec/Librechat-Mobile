package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ToolsHiddenReason

@PreviewNarrowScreens
@Composable
private fun ToolsSheetDefaultMaterialPreview() = SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(gauge = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetDefaultGlassPreview() = SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(gauge = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetComparingMaterialPreview() = SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(comparing = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetComparingGlassPreview() = SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(comparing = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetAgentNoteMaterialPreview() =
    SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(hidden = ToolsHiddenReason.AGENT) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetAgentNoteGlassPreview() =
    SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(hidden = ToolsHiddenReason.AGENT) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetSpecNoteMaterialPreview() =
    SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(hidden = ToolsHiddenReason.SPEC) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetSpecNoteGlassPreview() =
    SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(hidden = ToolsHiddenReason.SPEC) }

/** Every tool and four MCP servers open, with a spec-locked tool and server and one needing sign-in. */
@PreviewNarrowScreens
@Composable
private fun ToolsSheetEverythingMaterialPreview() = SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(everything = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetEverythingGlassPreview() = SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(everything = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetCodeUploadMaterialPreview() = SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(codeUpload = true) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetCodeUploadGlassPreview() = SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(codeUpload = true) }

/** No model picker on this server: Parameters takes a labelled row of its own. */
@PreviewNarrowScreens
@Composable
private fun ToolsSheetNoPickerMaterialPreview() = SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(picker = false) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetNoPickerGlassPreview() = SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(picker = false) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetKeyMissingMaterialPreview() =
    SheetSurface(UiStyle.MATERIAL) { ToolsSheetHost(keyHint = KeyHint.NEEDS_KEY) }

@PreviewNarrowScreens
@Composable
private fun ToolsSheetKeyMissingGlassPreview() =
    SheetSurface(UiStyle.LIQUID_GLASS) { ToolsSheetHost(keyHint = KeyHint.NEEDS_KEY) }

@Composable
private fun SheetSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
    }
}

@Composable
private fun ToolsSheetHost(
    gauge: Boolean = false,
    comparing: Boolean = false,
    hidden: ToolsHiddenReason? = null,
    everything: Boolean = false,
    codeUpload: Boolean = false,
    picker: Boolean = true,
    keyHint: KeyHint? = null,
) {
    var gaugeExpanded by remember { mutableStateOf(false) }
    val lockedTools = if (everything) setOf(ToolConstants.WEB_SEARCH) else emptySet()
    val lockedServers = if (everything) setOf("company-docs") else emptySet()
    ChatToolsSheetContent(
        enabledTools = setOf(ToolConstants.CODE_INTERPRETER, ToolConstants.SKILLS),
        onToggleTool = {},
        mcpServers = if (everything) previewServers else previewServers.take(2),
        selectedMcpServerNames = setOf("github"),
        onToggleMcpServer = {},
        onAttachFiles = {},
        onTakePhoto = {},
        onPickPhotos = {},
        onAttachFromServer = {},
        onOpenModelParameters = {},
        onOpenModelSelector = {},
        selectedModelDisplay = if (everything) "Research Assistant (company knowledge base)" else "gpt-5.1",
        onDismiss = {},
        selection = OptionsSelection(
            modelIcon = IconSource(endpointName = "openAI", imageUrl = null),
            modelSubtitle = if (everything) "gpt-5.1" else null,
            modelKeyHint = keyHint,
            lockedBy = if (everything) "Research Assistant" else null,
        ),
        toolsHiddenReason = hidden,
        comparisonModels = if (comparing) ComparisonModelLabels("gpt-5.1", "claude-opus-4-5") else null,
        openTools = if (everything) {
            setOf(
                ToolConstants.WEB_SEARCH,
                ToolConstants.URL_CONTEXT,
                ToolConstants.CODE_INTERPRETER,
                ToolConstants.FILE_SEARCH,
                ToolConstants.MEMORY,
                ToolConstants.SKILLS,
                ToolConstants.ARTIFACTS,
            )
        } else {
            setOf(
                ToolConstants.WEB_SEARCH,
                ToolConstants.CODE_INTERPRETER,
                ToolConstants.FILE_SEARCH,
                ToolConstants.MEMORY,
                ToolConstants.SKILLS,
            )
        },
        gates = ChatInputGates(
            modelPickerShown = picker,
            showEphemeralTools = hidden == null,
            lockedTools = lockedTools,
            lockedMcpServers = lockedServers,
        ),
        contextGauge = gaugeWithNudge.takeIf { gauge },
        contextUsageEnabled = gauge,
        onCompact = {},
        contextGaugeExpanded = gaugeExpanded,
        onContextGaugeExpandedChange = { gaugeExpanded = it },
        mcpExpanded = everything,
        onAttachToCode = if (codeUpload) ({}) else null,
        onSignInMcpServer = {},
        onCancelMcpSignIn = {},
    )
}

private val previewServers = listOf(
    McpServerDisplayData("github", "GitHub", "Issues, pull requests and code search", McpDisplayStatus.CONNECTED),
    McpServerDisplayData("linear", "Linear", "Project tracking", McpDisplayStatus.NEEDS_SIGN_IN, requiresOAuth = true),
    McpServerDisplayData("company-docs", "Company docs", null, McpDisplayStatus.CONNECTED),
    McpServerDisplayData("weather", null, "Forecasts by city", McpDisplayStatus.ON_DEMAND),
    McpServerDisplayData("calendar", "Team calendar", "Meetings", McpDisplayStatus.CONNECTING, requiresOAuth = true),
)
