package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.ui.artifacts.artifactsModeName
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveTile
import com.garfiec.librechat.core.ui.components.AdaptiveToggleTile
import com.garfiec.librechat.core.ui.components.DragSelectRowShape
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.TileStatusDot
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.core.ui.mcp.mcpStatusColor
import com.garfiec.librechat.core.ui.mcp.mcpStatusLabel
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.util.SETTING_TOOL_ORDER
import com.garfiec.librechat.feature.chat.util.ToolsSheetFocus
import com.garfiec.librechat.feature.chat.util.WebSearchKeysRow
import com.garfiec.librechat.feature.chat.util.WebSearchKind
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import com.garfiec.librechat.feature.chat.viewmodel.ToolsHiddenReason
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

/** What the Options page says about the selection: the Model cell's icon and lines, and who locks spec tools. */
@Immutable
data class OptionsSelection(
    val modelIcon: IconSource? = null,
    /** The Model cell's second line: the model the selection runs, when the title doesn't name it. */
    val modelSubtitle: String? = null,
    /** The selected endpoint's missing or expired key, said on the Model cell in place of [modelSubtitle]. */
    val modelKeyHint: KeyHint? = null,
    /** The active spec's label, for "Set by …" on the tools and MCP servers it locks. */
    val lockedBy: String? = null,
)

/**
 * The scrollable body of the chat tools/attachment menu (the Options page), no sheet chrome.
 * Rendered by both [ChatOptionsBottomSheet] and the pull-up surface in `ChatScreen`.
 *
 * The Model cell and Parameters button do *not* call [onDismiss] (the paged sheet swaps in place, the
 * pull-up hands off); the attach tiles and the code-upload row call their action then [onDismiss];
 * tool tiles and MCP rows toggle in place.
 */
@Composable
fun ChatToolsSheetContent(
    enabledTools: Set<String>,
    onToggleTool: (String) -> Unit,
    mcpServers: List<McpServerDisplayData>,
    selectedMcpServerNames: Set<String>,
    onToggleMcpServer: (String) -> Unit,
    onAttachFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onAttachFromServer: () -> Unit,
    onOpenModelParameters: () -> Unit,
    onOpenModelSelector: () -> Unit,
    selectedModelDisplay: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selection: OptionsSelection = OptionsSelection(),
    /** Why the tool controls are hidden ([ChatInputGates.showEphemeralTools] off); a note takes their place. */
    toolsHiddenReason: ToolsHiddenReason? = null,
    /** Both models while comparing: the Model cell then names A and B rather than A alone. */
    comparisonModels: ComparisonModelLabels? = null,
    /**
     * [ChatUiState.openTools]: a tile for each, in [SETTING_TOOL_ORDER]. The Skills tile's state is per
     * conversation; it reads it from [enabledTools] like the others.
     */
    openTools: Set<String> = emptySet(),
    /** The search the web-search control runs when on, named on its state line. */
    webSearchKind: WebSearchKind = WebSearchKind(WebSearchRoute.LIBRECHAT, null),
    mcpServersEnabled: Boolean = true,
    /**
     * Server/endpoint feature gates for the sheet: model-select row, parameters button,
     * ephemeral tool controls (web search / code / file search / MCP), and the
     * Camera / Photos / Files attach controls. See [ChatInputGates].
     */
    gates: ChatInputGates = ChatInputGates(),
    /** The context gauge's reading and breakdown; drives the optional gauge above the model row. */
    contextGauge: ContextGaugeDetails? = null,
    /** Server/version gate for the context gauge (`interface.contextUsage` AND backend ≥ 0.8.7). */
    contextUsageEnabled: Boolean = false,
    onCompact: (() -> Unit)? = null,
    /** "Not now" on a compact suggestion. */
    onSnoozeCompact: () -> Unit = {},
    /** Where the user chose to surface the gauge; the sheet only renders it when [ContextBarPlacement.OPTIONS_SHEET]. */
    contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    /** Persisted expanded/collapsed state of the gauge's inline breakdown. */
    contextGaugeExpanded: Boolean = false,
    onContextGaugeExpandedChange: (Boolean) -> Unit = {},
    /** MCP sub-list expansion, hoisted so the paged sheet keeps it across a page swap (which drops this composable). */
    mcpExpanded: Boolean = false,
    onMcpExpandedChange: (Boolean) -> Unit = {},
    /** Scroll position, hoisted for the same reason as [mcpExpanded]. Defaulted for hosts that keep it composed. */
    scrollState: ScrollState = rememberScrollState(),
    /** "Upload to code environment" (legacy upload UX); null hides the row. */
    onAttachToCode: (() -> Unit)? = null,
    /**
     * The row to scroll to and mark as the page opens (a settings chip's tap): the MCP list opens for
     * a server, so its row is there to turn it off.
     */
    focus: ToolsSheetFocus? = null,
    /** Signs a server in from its row, turning it on once it connects; null offers no sign-in. */
    onSignInMcpServer: ((String) -> Unit)? = null,
    onCancelMcpSignIn: ((String) -> Unit)? = null,
    /** Opens a server's variables sheet (this sheet closes); null offers no setup. */
    onSetUpMcpServer: ((String) -> Unit)? = null,
    /** The LibreChat search key row under the tool grid; null hides it. */
    webSearchKeys: WebSearchKeysRow? = null,
    /** Opens the search key sheet, from the row or a Web Search tile that has no search to run without keys. */
    onOpenWebSearchKeys: () -> Unit = {},
    /** The Artifacts tile's mode, named on its state line when it isn't the default. */
    artifactsMode: ArtifactsMode = ArtifactsMode.DEFAULT,
    /** Opens the mode choice, on a long-press of the Artifacts tile. */
    onOpenArtifactsMode: () -> Unit = {},
) {
    val dragSelection = rememberListDragSelection()
    val expandMcp by rememberUpdatedState(onMcpExpandedChange)
    LaunchedEffect(focus) {
        if (focus is ToolsSheetFocus.McpServer && !mcpExpanded) expandMcp(true)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Tall configurations exceed the sheet height, so the content scrolls.
            .verticalScroll(scrollState)
            .listDragSelection(dragSelection, holdOnly = true)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        // Media and files only, hidden when the selected endpoint can't accept uploads (web's
        // AttachFileChat gate). "Server" attaches an already-uploaded file by reference.
        if (gates.fileUploadEnabled) {
            AttachRow(
                onTakePhoto = onTakePhoto,
                onPickPhotos = onPickPhotos,
                onAttachFiles = onAttachFiles,
                onAttachFromServer = onAttachFromServer,
                onDismiss = onDismiss,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Context-usage gauge, surfaced here when the user picked the options-sheet placement.
        // Full-width; tapping expands the breakdown inline (no nested modal sheet).
        val sheetContextGauge = contextGauge
        if (contextBarPlacement == ContextBarPlacement.OPTIONS_SHEET &&
            contextUsageEnabled &&
            sheetContextGauge != null &&
            sheetContextGauge.usage.usedTokens > 0
        ) {
            ContextUsageExpandableGauge(
                details = sheetContextGauge,
                expanded = contextGaugeExpanded,
                onExpandedChange = onContextGaugeExpandedChange,
                onCompact = onCompact,
                onSnoozeCompact = onSnoozeCompact,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // The picker hidden (ChatUiState.modelPickerShown) leaves a lone Parameters icon unlabeled,
        // so Parameters then takes a labelled row of its own.
        if (gates.modelPickerShown) {
            AdaptiveSectionHeader(title = stringResource(Res.string.tool_model), horizontalInset = SectionInset)
            ModelRow(
                selectedModelDisplay = selectedModelDisplay,
                selection = selection,
                comparisonModels = comparisonModels,
                onOpenModelSelector = onOpenModelSelector,
                onOpenModelParameters = onOpenModelParameters.takeIf { gates.parametersEnabled },
            )
        } else if (gates.parametersEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            SheetActionRow(
                icon = Icons.Default.Tune,
                label = stringResource(Res.string.tool_model_parameters),
                onClick = onOpenModelParameters,
            )
        }

        // Ephemeral tools are hidden for the agents endpoint (the agent uses its own configured tools,
        // web's showEphemeralBadges) and under a `hideBadgeRow` spec. Each tool's icon/label comes from
        // the shared [ephemeralToolMeta]; its gate is ChatUiState.toolGateOpen, as on the settings line.
        if (gates.showEphemeralTools) {
            val shownTools = SETTING_TOOL_ORDER.filter { it in openTools && ephemeralToolMeta(it) != null }
            if (shownTools.isNotEmpty()) {
                val onCount = shownTools.count { it in gates.lockedTools || it in enabledTools }
                AdaptiveSectionHeader(
                    title = pluralStringResource(Res.plurals.section_tools, onCount, onCount),
                    horizontalInset = SectionInset,
                )
                ToolGrid(
                    tools = shownTools,
                    enabledTools = enabledTools,
                    lockedTools = gates.lockedTools,
                    lockedBy = selection.lockedBy,
                    webSearchKind = webSearchKind,
                    artifactsMode = artifactsMode,
                    onOpenArtifactsMode = onOpenArtifactsMode,
                    onToggleTool = { key ->
                        if (key == ToolConstants.WEB_SEARCH && key !in enabledTools && webSearchKeys?.opensOnTurnOn == true) {
                            onOpenWebSearchKeys()
                            onDismiss()
                        } else {
                            onToggleTool(key)
                        }
                    },
                    focus = focus,
                )
            }
            if (webSearchKeys != null) {
                Spacer(modifier = Modifier.height(8.dp))
                WebSearchKeysActionRow(
                    row = webSearchKeys,
                    onClick = {
                        onOpenWebSearchKeys()
                        onDismiss()
                    },
                )
            }
        } else if (toolsHiddenReason != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ToolsHiddenNote(toolsHiddenReason)
        }

        if (onAttachToCode != null) {
            Spacer(modifier = Modifier.height(8.dp))
            SheetActionRow(
                icon = Icons.Default.Terminal,
                label = stringResource(Res.string.attach_code_environment),
                onClick = {
                    onAttachToCode()
                    onDismiss()
                },
            )
        }

        // MCP section — hidden when role denies MCP_SERVERS.USE, and for the agents
        // endpoint where dynamic MCP selections are silently ignored by the backend.
        if (gates.showEphemeralTools && mcpServersEnabled && mcpServers.isNotEmpty()) {
            // The header is a tappable row: keep its press highlight off the tiles above.
            Spacer(modifier = Modifier.height(8.dp))
            McpSectionHeader(
                servers = mcpServers,
                onCount = mcpServers.count { it.name in gates.lockedMcpServers || it.name in selectedMcpServerNames },
                expanded = mcpExpanded,
                onToggle = { onMcpExpandedChange(!mcpExpanded) },
                dragSelection = dragSelection,
            )
            if (mcpExpanded) {
                mcpServers.forEach { server ->
                    val locked = server.name in gates.lockedMcpServers
                    McpServerToggleRow(
                        modifier = focusedRow(focus == ToolsSheetFocus.McpServer(server.name), DragSelectRowShape),
                        server = server,
                        isSelected = locked || server.name in selectedMcpServerNames,
                        onToggle = { onToggleMcpServer(server.name) },
                        dragSelection = dragSelection,
                        lockedBy = selection.lockedBy.takeIf { locked },
                        locked = locked,
                        onSignIn = onSignInMcpServer?.let { { it(server.name) } },
                        onCancelSignIn = onCancelMcpSignIn?.let { { it(server.name) } },
                        onSetUp = onSetUpMcpServer?.let {
                            {
                                it(server.name)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun focusedRow(focused: Boolean, shape: Shape): Modifier {
    if (!focused) return Modifier
    val requester = remember { BringIntoViewRequester() }
    val wash = remember { Animatable(1f) }
    val color = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) {
        // After the page's first layout, so there is somewhere to scroll to.
        withFrameNanos {}
        requester.bringIntoView()
        delay(FOCUS_HOLD_MS)
        wash.animateTo(0f, tween(FOCUS_FADE_MS))
    }
    return Modifier
        .bringIntoViewRequester(requester)
        // Over the content in the control's own shape: a tile's opaque fill would hide a mark behind it.
        .drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), color.copy(alpha = FOCUS_WASH_ALPHA * wash.value))
        }
}

private const val FOCUS_HOLD_MS = 700L
private const val FOCUS_FADE_MS = 900
private const val FOCUS_WASH_ALPHA = 0.14f

private val SectionInset = 16.dp
private val TileGap = 8.dp
private val TileInset = 12.dp
private val TileShape = RoundedCornerShape(16.dp)
private val AttachCardMinHeight = 80.dp
private val CellMinHeight = 60.dp

/**
 * Camera, Photos, Files and Server: four equal cards side by side, filling the row, each at least
 * 80dp tall; a label wraps to two lines at 320dp and the row's cards share one height.
 */
@Composable
private fun AttachRow(
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onAttachFiles: () -> Unit,
    onAttachFromServer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        Triple(Icons.Default.PhotoCamera, Res.string.tool_camera, onTakePhoto),
        Triple(Icons.Default.PhotoLibrary, Res.string.tool_photos, onPickPhotos),
        Triple(Icons.Default.AttachFile, Res.string.tool_files, onAttachFiles),
        Triple(Icons.Default.CloudDownload, Res.string.tool_server_files, onAttachFromServer),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = TileInset),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        options.forEach { (icon, labelRes, action) ->
            AdaptiveTile(
                onClick = {
                    action()
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = AttachCardMinHeight),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The Model cell, and the icon-only Parameters button at its end when [onOpenModelParameters] is set. */
@Composable
private fun ModelRow(
    selectedModelDisplay: String?,
    selection: OptionsSelection,
    comparisonModels: ComparisonModelLabels?,
    onOpenModelSelector: () -> Unit,
    onOpenModelParameters: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = TileInset),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        AdaptiveTile(
            onClick = onOpenModelSelector,
            modifier = Modifier.weight(1f).heightIn(min = CellMinHeight),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CellMinHeight)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Comparing, the A/B badges name the lanes; one icon would speak for lane A only.
                val modelIcon = selection.modelIcon
                if (comparisonModels == null && modelIcon != null) {
                    EndpointIcon(
                        endpointName = modelIcon.endpointName,
                        iconUrl = modelIcon.imageUrl,
                        provider = modelIcon.provider,
                        size = 32.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (comparisonModels != null) {
                        Text(
                            text = stringResource(Res.string.compare_models),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ComparisonModelsLine(comparisonModels)
                    } else {
                        Text(
                            text = selectedModelDisplay ?: stringResource(Res.string.select_model),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val keyText = when (selection.modelKeyHint) {
                            KeyHint.NEEDS_KEY -> stringResource(Res.string.model_selector_needs_key)
                            KeyHint.EXPIRED -> stringResource(Res.string.model_selector_key_expired)
                            null -> null
                        }
                        val second = keyText ?: selection.modelSubtitle
                        if (second != null) {
                            Text(
                                text = second,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (keyText != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (onOpenModelParameters != null) {
            AdaptiveTile(
                onClick = onOpenModelParameters,
                modifier = Modifier.width(CellMinHeight).fillMaxHeight(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(Res.string.tool_model_parameters),
                    )
                }
            }
        }
    }
}

/** The search key row: set-up wording until the user's keys work, then the providers they run. */
@Composable
internal fun WebSearchKeysActionRow(row: WebSearchKeysRow, onClick: () -> Unit) {
    SheetActionRow(
        icon = Icons.Default.VpnKey,
        label = stringResource(if (row.authenticated) Res.string.web_search_keys_title else Res.string.web_search_keys_setup),
        supporting = when {
            row.authenticated -> row.summary
            row.fallbackProvider != null -> stringResource(Res.string.web_search_keys_fallback, row.fallbackProvider)
            else -> stringResource(Res.string.web_search_keys_add)
        },
        onClick = onClick,
    )
}

/** A full-width labelled action on the page's tile surface (the code upload, Parameters without a picker). */
@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit, supporting: String? = null) {
    AdaptiveTile(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TileInset),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CellMinHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two tiles a row at every width; an odd last tile keeps half the width. */
@Composable
private fun ToolGrid(
    tools: List<String>,
    enabledTools: Set<String>,
    lockedTools: Set<String>,
    lockedBy: String?,
    webSearchKind: WebSearchKind,
    artifactsMode: ArtifactsMode,
    onOpenArtifactsMode: () -> Unit,
    onToggleTool: (String) -> Unit,
    focus: ToolsSheetFocus?,
) {
    val searchName = webSearchName(webSearchKind)
    val artifactsModeName = artifactsMode.takeIf { it != ArtifactsMode.DEFAULT }?.let { artifactsModeName(it) }
    val artifactsStyle = stringResource(CoreUiRes.string.artifacts_mode_title)
    val setBy = lockedByText(lockedBy)
    val on = stringResource(Res.string.tool_state_on)
    val off = stringResource(Res.string.tool_state_off)
    Column(
        modifier = Modifier.padding(horizontal = TileInset),
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        tools.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                pair.forEach { key ->
                    val meta = ephemeralToolMeta(key) ?: return@forEach
                    val locked = key in lockedTools
                    val checked = locked || key in enabledTools
                    AdaptiveToggleTile(
                        checked = checked,
                        icon = meta.icon,
                        label = stringResource(meta.titleRes),
                        stateLabel = when {
                            locked && key == ToolConstants.WEB_SEARCH && searchName != null -> "$setBy · $searchName"
                            locked -> setBy
                            !checked -> off
                            key == ToolConstants.WEB_SEARCH -> searchName ?: on
                            key == ToolConstants.ARTIFACTS -> artifactsModeName ?: on
                            else -> on
                        },
                        onClick = { onToggleTool(key) },
                        modifier = focusedRow(focus == ToolsSheetFocus.Tool(key), TileShape)
                            .weight(1f)
                            .fillMaxHeight(),
                        locked = locked,
                        onLongClick = onOpenArtifactsMode.takeIf { key == ToolConstants.ARTIFACTS },
                        onLongClickLabel = artifactsStyle.takeIf { key == ToolConstants.ARTIFACTS },
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Where the tools come from when the page shows none: the agent's settings, or the spec's. */
@Composable
private fun ToolsHiddenNote(reason: ToolsHiddenReason) {
    AdaptiveTile(
        modifier = Modifier.fillMaxWidth().padding(horizontal = TileInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (reason) {
                    ToolsHiddenReason.AGENT -> {
                        Text(
                            text = stringResource(Res.string.tools_from_agent_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = stringResource(Res.string.tools_from_agent_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ToolsHiddenReason.SPEC -> Text(
                        text = stringResource(Res.string.tools_from_spec),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * "MCP servers · n of m on", styled as the page's other section headers so built-in tools never read
 * as part of MCP, with "k needs sign-in" under it and a chevron that flips with [expanded].
 */
@Composable
private fun McpSectionHeader(
    servers: List<McpServerDisplayData>,
    onCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    dragSelection: ListDragSelection,
) {
    val needSignIn = servers.count { it.signsInOnTap }
    val stateText = stringResource(if (expanded) Res.string.state_expanded else Res.string.state_collapsed)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    val title = stringResource(Res.string.mcp_servers_title) + " · " +
        pluralStringResource(Res.plurals.mcp_servers_on, servers.size, onCount, servers.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dragSelectRow(dragSelection, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AdaptiveSectionHeader(title = title, horizontalInset = SectionInset)
            if (needSignIn > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.mcp_needs_sign_in, needSignIn, needSignIn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = SectionInset, end = SectionInset, bottom = 8.dp),
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, end = SectionInset).rotate(rotation),
        )
    }
}

/**
 * The checkbox only shows the state. A selected server always toggles off from the row, whatever its status,
 * so nothing keeps an unusable server in the chat. Any server that needs sign-in shows "Sign in" (or "Cancel
 * sign-in" while pending) as its own button; on an unselected or locked one the row tap signs in, and a pending
 * one doesn't toggle. A server owed user variables shows "Set up" the same way, opening its variables sheet.
 */
@Composable
private fun McpServerToggleRow(
    server: McpServerDisplayData,
    isSelected: Boolean,
    onToggle: () -> Unit,
    dragSelection: ListDragSelection,
    lockedBy: String?,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onSignIn: (() -> Unit)? = null,
    onCancelSignIn: (() -> Unit)? = null,
    onSetUp: (() -> Unit)? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val setUp = onSetUp.takeIf { server.setsUpOnTap }
    val signIn = onSignIn.takeIf { server.signsInOnTap && setUp == null }
    val cancelSignIn = onCancelSignIn.takeIf { server.signInPending }
    val toggles = (isSelected && !locked) || (signIn == null && cancelSignIn == null && setUp == null)
    val rowClick: (() -> Unit)? = when {
        toggles && !locked -> onToggle
        setUp != null -> setUp
        signIn != null -> signIn.takeIf { !server.signInBusy }
        else -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // A row with no click has nothing to merge its texts, so it merges them itself.
            .semantics(mergeDescendants = rowClick == null && cancelSignIn == null) {
                if (toggles) toggleableState = ToggleableState(isSelected)
                if (toggles && locked) role = Role.Checkbox
                if ((toggles && locked) || (signIn != null && rowClick == null)) disabled()
            }
            .then(
                if (rowClick == null) {
                    Modifier
                } else {
                    Modifier.dragSelectRow(
                        dragSelection,
                        role = if (toggles) Role.Checkbox else Role.Button,
                        onClick = rowClick,
                    )
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialTile(seed = server.name, label = server.label, modifier = Modifier.dragSelectRowIcon()) {
            TileStatusDot(
                color = mcpStatusColor(server.status),
                ringColor = MaterialTheme.colorScheme.surfaceContainerLow,
                pulsing = server.status == McpDisplayStatus.CONNECTING && !reduceMotion,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = stringResource(mcpStatusLabel(server.status))
            val detail = if (locked) {
                lockedByText(lockedBy)
            } else {
                server.description?.takeIf { it.isNotBlank() }
            }
            Text(
                text = if (detail != null) "$status · $detail" else status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            setUp != null -> McpRowButton(Res.string.mcp_set_up_action, setUp, enabled = true)
            signIn != null -> McpRowButton(Res.string.mcp_sign_in_action, signIn, enabled = !server.signInBusy)
            cancelSignIn != null ->
                McpRowButton(Res.string.mcp_chip_cancel_sign_in, cancelSignIn, enabled = !server.signInBusy)
        }
        if (toggles) AdaptiveCheckbox(checked = isSelected, onCheckedChange = null, enabled = !locked)
    }
}

/** Takes at most half the width beside the tile, its label wrapping, so the server's name and status keep the rest. */
@Composable
private fun McpRowButton(label: StringResource, onClick: () -> Unit, enabled: Boolean) {
    AdaptiveFilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.layout { measurable, constraints ->
            val half = if (constraints.hasBoundedWidth) constraints.maxWidth / 2 else constraints.maxWidth
            val placeable = measurable.measure(constraints.copy(maxWidth = half.coerceAtLeast(constraints.minWidth)))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    ) {
        Text(stringResource(label), maxLines = 2, textAlign = TextAlign.Center)
    }
}
