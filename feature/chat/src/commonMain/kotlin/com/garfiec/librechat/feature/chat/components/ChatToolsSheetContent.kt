package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.DragSelectRowShape
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.core.ui.mcp.mcpStatusColor
import com.garfiec.librechat.core.ui.mcp.mcpStatusLabel
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.SETTING_TOOL_ORDER
import com.garfiec.librechat.feature.chat.util.ToolsSheetFocus
import com.garfiec.librechat.feature.chat.util.WebSearchKind
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The scrollable body of the chat tools/attachment menu (the Options page), no sheet chrome.
 * Rendered by both [ChatOptionsBottomSheet] and the pull-up surface in `ChatScreen`.
 *
 * The Model / Model Parameters rows do *not* call [onDismiss] (the paged sheet swaps in place, the
 * pull-up hands off); every other row calls its action then [onDismiss].
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
    /** Both models while comparing: the Model row then names A and B rather than A alone. */
    comparisonModels: ComparisonModelLabels? = null,
    /**
     * [ChatUiState.openTools]: a row for each, in [SETTING_TOOL_ORDER]. The Skills row's state is per
     * conversation; it reads it from [enabledTools] like the others.
     */
    openTools: Set<String> = emptySet(),
    /** The search the web-search control runs when on, named on its second line. */
    webSearchKind: WebSearchKind = WebSearchKind(WebSearchRoute.LIBRECHAT, null),
    mcpServersEnabled: Boolean = true,
    /**
     * Server/endpoint feature gates for the sheet: model-select row, parameters row,
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
    /** "Upload to Code Environment" (legacy upload UX); null hides the card. */
    onAttachToCode: (() -> Unit)? = null,
    /**
     * The row to scroll to and mark as the page opens (a settings chip's tap): the MCP list opens for
     * a server, so its switch is there to turn it off.
     */
    focus: ToolsSheetFocus? = null,
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
        // Top section: Camera, Photos, Files, Server cards — hidden when the selected
        // endpoint can't accept uploads (mirrors web's AttachFileChat gate). "Server"
        // attaches an already-uploaded file by reference (no re-upload).
        if (gates.fileUploadEnabled) {
            AttachmentOptionCards(
                onTakePhoto = onTakePhoto,
                onPickPhotos = onPickPhotos,
                onAttachFiles = onAttachFiles,
                onAttachFromServer = onAttachFromServer,
                onAttachToCode = onAttachToCode,
                onDismiss = onDismiss,
            )

            Spacer(modifier = Modifier.height(12.dp))
            AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
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
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }

        // Model selector row: hidden when the server offers no picker (see ChatUiState.modelPickerShown).
        if (gates.modelPickerShown) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // No onDismiss — see the KDoc above.
                    .dragSelectRow(dragSelection, onClick = onOpenModelSelector)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).dragSelectRowIcon(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (comparisonModels != null) {
                        // Comparing: the row names both models, as the A/B pair the selector opens on.
                        Text(
                            text = stringResource(Res.string.compare_models),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        ComparisonModelsLine(comparisonModels)
                    } else {
                        Text(
                            text = stringResource(Res.string.tool_model),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = selectedModelDisplay ?: stringResource(Res.string.select_model),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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

        // Model Parameters — hidden when the server disables `interface.parameters`.
        if (gates.parametersEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dragSelectRow(dragSelection, onClick = onOpenModelParameters)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).dragSelectRowIcon(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.tool_model_parameters),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.tool_model_parameters_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AdaptiveDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        // Tool toggle items — ephemeral tools are hidden for the agents endpoint
        // (the agent uses its own configured tools), mirroring web's showEphemeralBadges.
        // Each tool's icon/label comes from the shared [ephemeralToolMeta]; its gate is
        // ChatUiState.toolGateOpen, the one the settings line shows its chip by.
        if (gates.showEphemeralTools) {
            val searchName = webSearchName(webSearchKind)
            SETTING_TOOL_ORDER.forEach { toolKey ->
                val meta = ephemeralToolMeta(toolKey)
                if (toolKey in openTools && meta != null) {
                    val locked = toolKey in gates.lockedTools
                    val name = searchName.takeIf { toolKey == ToolConstants.WEB_SEARCH }
                    ToolToggleRow(
                        modifier = focusedRow(focus == ToolsSheetFocus.Tool(toolKey)),
                        icon = meta.icon,
                        title = stringResource(meta.titleRes),
                        subtitle = when {
                            name != null && locked -> stringResource(Res.string.web_search_set_by_spec, name)
                            name != null -> name
                            else -> stringResource(if (locked) Res.string.tool_set_by_spec else meta.descriptionRes)
                        },
                        isEnabled = locked || toolKey in enabledTools,
                        onToggle = { onToggleTool(toolKey) },
                        dragSelection = dragSelection,
                        locked = locked,
                    )
                }
            }
        }

        // MCP section — hidden when role denies MCP_SERVERS.USE, and for the agents
        // endpoint where dynamic MCP selections are silently ignored by the backend.
        if (gates.showEphemeralTools && mcpServersEnabled && mcpServers.isNotEmpty()) {
            val anyMcpSelected = selectedMcpServerNames.isNotEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dragSelectRow(dragSelection) { onMcpExpandedChange(!mcpExpanded) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).dragSelectRowIcon(),
                    tint = if (anyMcpSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.tool_mcp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.tool_mcp_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (anyMcpSelected) {
                    Text(
                        text = "${selectedMcpServerNames.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            // MCP server sub-list
            if (mcpExpanded) {
                Column(
                    modifier = Modifier.padding(start = 40.dp),
                ) {
                    mcpServers.forEach { server ->
                        val locked = server.name in gates.lockedMcpServers
                        McpServerToggleRow(
                            modifier = focusedRow(focus == ToolsSheetFocus.McpServer(server.name)),
                            server = server,
                            isSelected = locked || server.name in selectedMcpServerNames,
                            onToggle = { onToggleMcpServer(server.name) },
                            dragSelection = dragSelection,
                            locked = locked,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun focusedRow(focused: Boolean): Modifier {
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
        // The row's own press shape, so the mark reads as the row being touched.
        .drawBehind {
            drawOutline(DragSelectRowShape.createOutline(size, layoutDirection, this), color.copy(alpha = FOCUS_WASH_ALPHA * wash.value))
        }
}

private const val FOCUS_HOLD_MS = 700L
private const val FOCUS_FADE_MS = 900
private const val FOCUS_WASH_ALPHA = 0.14f

@Composable
private fun ToolToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    dragSelection: ListDragSelection,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The platform reads the on/off state out in the user's language. The title and
            // subtitle below name the row, so no description is set on top of them.
            // A locked row has no click to merge its texts, so it merges them itself.
            .semantics(mergeDescendants = locked) {
                toggleableState = ToggleableState(isEnabled)
                if (locked) {
                    role = Role.Switch
                    disabled()
                }
            }
            .then(if (locked) Modifier else Modifier.dragSelectRow(dragSelection, role = Role.Switch, onClick = onToggle))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).dragSelectRowIcon(),
            tint = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row is the control (it carries the state, and its texts name the tool); a clickable
        // switch inside it would be a second, unnamed toggle to a screen reader.
        AdaptiveSwitch(
            checked = isEnabled,
            onCheckedChange = null,
            enabled = !locked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}

/**
 * Camera, Photos, Files and Server (and Code, with [onAttachToCode]): one row of four, or two rows of
 * two below a 360dp sheet, where a quarter of the width leaves a translated label (Appareil photo,
 * Kamera) only its first letters. Five cards take rows of three.
 */
@Composable
private fun AttachmentOptionCards(
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onAttachFiles: () -> Unit,
    onAttachFromServer: () -> Unit,
    onAttachToCode: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val options = listOfNotNull(
        Triple(Icons.Default.PhotoCamera, Res.string.tool_camera, onTakePhoto),
        Triple(Icons.Default.PhotoLibrary, Res.string.tool_photos, onPickPhotos),
        Triple(Icons.Default.AttachFile, Res.string.tool_files, onAttachFiles),
        Triple(Icons.Default.CloudDownload, Res.string.tool_server_files, onAttachFromServer),
        onAttachToCode?.let { Triple(Icons.Default.Terminal, Res.string.tool_code_environment, it) },
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val perRow = when {
            maxWidth < ATTACH_CARDS_ONE_ROW_MIN_WIDTH -> 2
            options.size > ATTACH_CARDS_MAX_PER_ROW -> ATTACH_CARDS_WRAPPED_PER_ROW
            else -> options.size
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (icon, label, action) ->
                        AttachmentOptionCard(
                            icon = icon,
                            label = stringResource(label),
                            onClick = {
                                action()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // A short last row keeps the others' card width.
                    repeat(perRow - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val ATTACH_CARDS_ONE_ROW_MIN_WIDTH = 360.dp
private const val ATTACH_CARDS_MAX_PER_ROW = 4
private const val ATTACH_CARDS_WRAPPED_PER_ROW = 3

@Composable
private fun AttachmentOptionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun McpServerToggleRow(
    server: McpServerDisplayData,
    isSelected: Boolean,
    onToggle: () -> Unit,
    dragSelection: ListDragSelection,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (locked) {
                    Modifier.semantics(mergeDescendants = true) { disabled() }
                } else {
                    Modifier.dragSelectRow(dragSelection, onClick = onToggle)
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The connection, as the settings chip and the MCP servers page show it.
        val statusLabel = stringResource(mcpStatusLabel(server.status))
        Box(
            modifier = Modifier
                .size(8.dp)
                .dragSelectRowIcon()
                .background(color = mcpStatusColor(server.status), shape = CircleShape)
                .semantics { contentDescription = statusLabel },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = if (locked) stringResource(Res.string.tool_set_by_spec) else server.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AdaptiveCheckbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = !locked,
        )
    }
}
