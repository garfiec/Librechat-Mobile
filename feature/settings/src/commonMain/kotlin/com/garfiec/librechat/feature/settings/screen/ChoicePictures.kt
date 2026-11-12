package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.ui.components.windowAutomaticArtifactPresentation
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.artifact_mode_here
import org.jetbrains.compose.resources.stringResource

/**
 * Chats › Artifacts open in: chosen by the window (Automatic, which names what it does in this one), a
 * sheet over the chat, or a page of its own.
 */
@Composable
internal fun ArtifactViewerChoice(
    selected: ArtifactDisplayMode,
    onSelect: (ArtifactDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val automaticHere =
        stringResource(Res.string.artifact_mode_here, artifactPresentationLabel(windowAutomaticArtifactPresentation()))
    PictureChoiceGroup(
        options = ArtifactDisplayMode.entries,
        selected = selected,
        onSelect = onSelect,
        label = { artifactDisplayModeLabel(it) },
        detail = { if (it == ArtifactDisplayMode.AUTOMATIC) automaticHere else null },
        modifier = modifier,
    ) { mode ->
        when (mode) {
            ArtifactDisplayMode.AUTOMATIC -> Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                MiniChat(modifier = Modifier.weight(1f)) {}
                Box(modifier = Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 32.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA), CircleShape),
                    )
                }
                Column(modifier = Modifier.weight(SPLIT_ARTIFACT_WEIGHT).fillMaxHeight()) {
                    MiniBar(leading = { BarIcon(Icons.Default.Close) })
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp, bottom = 12.dp)) {
                        ArtifactBody()
                    }
                }
            }
            ArtifactDisplayMode.BOTTOM_SHEET -> MiniChat {
                Scrim()
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(SHEET_HEIGHT)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, SheetShape)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Handle()
                    ArtifactBody()
                }
            }
            ArtifactDisplayMode.FULLSCREEN -> Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            ) {
                MiniBar(leading = { BarIcon(Icons.Default.Close) })
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ArtifactBody()
                }
            }
        }
    }
}

/** Context usage › Where it shows: a chat with the gauge's spot picked out, or no gauge at all. */
@Composable
internal fun ContextPlacementChoice(
    selected: ContextBarPlacement,
    onSelect: (ContextBarPlacement) -> Unit,
    modifier: Modifier = Modifier,
) {
    PictureChoiceGroup(
        options = ContextBarPlacement.entries,
        selected = selected,
        onSelect = onSelect,
        label = { contextBarPlacementLabel(it) },
        modifier = modifier,
    ) { placement ->
        MiniChat(gaugeInComposer = placement == ContextBarPlacement.ABOVE_INPUT) {
            when (placement) {
                ContextBarPlacement.OPTIONS_SHEET -> {
                    Scrim()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, SheetShape)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Handle()
                        MenuLines()
                    }
                }
                ContextBarPlacement.OVERFLOW_MENU -> Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .width(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MenuShape)
                        .padding(12.dp),
                ) {
                    MenuLines()
                }
                ContextBarPlacement.HIDDEN, ContextBarPlacement.ABOVE_INPUT -> Unit
            }
        }
    }
}

/** Models & keys › Starred models: the model picker with starred entries left in place, grouped or lifted. */
@Composable
internal fun StarredModelsChoice(
    selected: StarredModelsDisplay,
    onSelect: (StarredModelsDisplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    PictureChoiceGroup(
        options = StarredModelsDisplay.entries,
        selected = selected,
        onSelect = onSelect,
        label = { starredModelsDisplayLabel(it) },
        modifier = modifier,
    ) { display ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (display) {
                StarredModelsDisplay.OFF -> {
                    ModelLine(0.5f)
                    ModelLine(0.65f, starred = true)
                    ModelLine(0.45f)
                    ModelLine(0.55f, starred = true)
                    ModelLine(0.6f)
                }
                StarredModelsDisplay.GROUPED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = GROUP_ALPHA), MenuShape)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Bar(fraction = 0.4f, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ExpandLess, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        ModelLine(0.65f, starred = true)
                        ModelLine(0.55f, starred = true)
                    }
                    ModelLine(0.5f)
                    ModelLine(0.45f)
                    ModelLine(0.6f)
                }
                StarredModelsDisplay.TOP -> {
                    ModelLine(0.65f, starred = true)
                    ModelLine(0.55f, starred = true)
                    ModelLine(0.5f)
                    ModelLine(0.45f)
                    ModelLine(0.6f)
                }
            }
        }
    }
}

/** A chat: bar, a conversation drawn as lines, and the message box, with [overlay] drawn over it all. */
@Composable
private fun MiniChat(
    modifier: Modifier = Modifier,
    gaugeInComposer: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val line = colors.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA)
    Box(modifier = modifier.fillMaxSize().background(colors.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            MiniBar(leading = { BarIcon(Icons.Default.Menu) }, trailing = { BarIcon(Icons.Default.MoreVert) })
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .fillMaxWidth(BUBBLE_WIDTH)
                        .background(colors.surfaceContainerHigh, BubbleShapeSample)
                        .padding(16.dp),
                ) { SampleLines(listOf(1f, 0.6f), line) }
                SampleLines(listOf(1f, 0.9f, 0.95f, 0.5f), line)
            }
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Default.Add)
                Spacer(Modifier.width(12.dp))
                Bar(fraction = 0.5f, color = line, modifier = Modifier.weight(1f))
                if (gaugeInComposer) Gauge()
            }
        }
        overlay()
    }
}

@Composable
private fun MiniBar(leading: @Composable () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Bar(fraction = 0.45f, color = MaterialTheme.colorScheme.onSurface.copy(alpha = SAMPLE_LINE_ALPHA), modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun BarIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A rounded bar [fraction] of the width it's given. */
@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(Modifier.fillMaxWidth(fraction).height(12.dp).background(color, CircleShape))
    }
}

/** The context gauge, picked out in the accent: the one thing each placement picture is about. */
@Composable
private fun Gauge() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .border(2.dp, colors.primary, CircleShape)
            .background(colors.primaryContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.width(64.dp).height(8.dp).background(colors.surfaceContainerHighest, CircleShape)) {
            Box(Modifier.fillMaxWidth(GAUGE_FILL).fillMaxHeight().background(colors.primary, CircleShape))
        }
    }
}

/** Three menu entries, the middle one holding the gauge. */
@Composable
private fun MenuLines() {
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA)
    val entry = Modifier.fillMaxWidth().height(40.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = entry, verticalAlignment = Alignment.CenterVertically) {
            Bar(fraction = 0.6f, color = line, modifier = Modifier.weight(1f))
        }
        Row(modifier = entry, verticalAlignment = Alignment.CenterVertically) { Gauge() }
        Row(modifier = entry, verticalAlignment = Alignment.CenterVertically) {
            Bar(fraction = 0.5f, color = line, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColumnScope.ArtifactBody() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(colors.surfaceContainerHighest, MenuShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.4f).height(16.dp).background(colors.primary, CircleShape))
        SampleLines(listOf(0.9f, 0.7f, 0.8f, 0.5f), colors.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA))
    }
}

@Composable
private fun ModelLine(fraction: Float, starred: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(colors.secondaryContainer, CircleShape))
        Spacer(Modifier.width(12.dp))
        Bar(fraction = fraction, color = colors.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA), modifier = Modifier.weight(1f))
        if (starred) Icon(Icons.Default.Star, null, Modifier.size(24.dp), tint = colors.primary)
    }
}

@Composable
private fun Handle() {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SAMPLE_LINE_ALPHA), CircleShape),
    )
}

@Composable
private fun BoxScope.Scrim() {
    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)))
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val MenuShape = RoundedCornerShape(12.dp)
private val BubbleShapeSample = RoundedCornerShape(20.dp)
private const val BUBBLE_WIDTH = 0.7f
private const val SHEET_HEIGHT = 0.6f
private const val SPLIT_ARTIFACT_WEIGHT = 0.7f
private const val GAUGE_FILL = 0.6f
private const val GROUP_ALPHA = 0.5f
private const val SCRIM_ALPHA = 0.32f
