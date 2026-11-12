package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton

/**
 * One choice in a picker of previews: [preview] laid out at a phone's width and scaled into the card, so
 * real controls keep their proportions, with [label] (and [detail] under it) and a radio below. The preview
 * never takes a touch.
 */
@Composable
internal fun PictureChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    preview: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(OptionShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, PreviewShape)
                .padding(if (selected) 2.dp else 1.dp)
                .clip(PreviewShape),
        ) {
            val scale = maxWidth / VirtualWidth
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(VirtualWidth, maxHeight / scale)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                preview()
            }
            // Over the preview and handling nothing: it takes the hit, so the preview's controls never see
            // a touch, and the tap falls through to the option's own selectable.
            Box(modifier = Modifier.matchParentSize().pointerInput(Unit) {})
        }
        // The radio above the label rather than beside it: a third of a phone's width leaves a label
        // beside a radio too little room, and it would break mid-word. A word wider than the cell
        // ("Predeterminado") is hyphenated where the platform can.
        AdaptiveRadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * [options] as [PictureChoice] cards in a radio group, as many to a row as fit at the current text size
 * and split into balanced rows (four as two by two); at large text they stack one to a row.
 */
@Composable
internal fun <T> PictureChoiceGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    detail: @Composable (T) -> String? = { null },
    picture: @Composable (T) -> Unit,
) {
    val minWidth = MinPictureWidth * LocalDensity.current.fontScale
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val fit = ((maxWidth + PictureGap) / (minWidth + PictureGap)).toInt().coerceIn(1, options.size)
            val rows = (options.size + fit - 1) / fit
            val perRow = (options.size + rows - 1) / rows
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(PictureGap)) {
                options.chunked(perRow).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PictureGap)) {
                        chunk.forEach { option ->
                            PictureChoice(
                                label = label(option),
                                selected = selected == option,
                                onClick = { onSelect(option) },
                                modifier = Modifier.weight(1f),
                                detail = detail(option),
                            ) { picture(option) }
                        }
                        // A short last row keeps the others' card width.
                        repeat(perRow - chunk.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/** A paragraph as rounded bars, one per line, each [widths] of the available width. */
@Composable
internal fun SampleLines(widths: List<Float>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widths.forEach { width ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(12.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

internal const val SAMPLE_LINE_ALPHA = 0.4f

private val OptionShape = RoundedCornerShape(16.dp)
private val PreviewShape = RoundedCornerShape(12.dp)
private val PreviewHeight = 148.dp

// The width the sample is laid out at before it is scaled into its card: a phone's.
private val VirtualWidth = 360.dp

// The narrowest card at 1x text; it widens with the text size, so a label keeps room for whole words.
private val MinPictureWidth = 104.dp
private val PictureGap = 12.dp
