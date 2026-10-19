package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.LiquidSegmentedControl
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * A single-choice row of text segments: an M3 segmented button row in Material, the iOS segmented
 * control ([LiquidSegmentedControl]) in Liquid Glass. The Material row takes its content's width unless
 * [modifier] fills; the iOS control always fills. When a label doesn't fit its segment on one line, the
 * same choice is stacked as radio rows instead: segmented buttons wrap a long label mid-word
 * ("Относите/льное" in ru) and the iOS control cuts it short, so the fit is measured rather than
 * guessed per locale.
 */
@Composable
fun AdaptiveSegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val radioList: @Composable () -> Unit = { ChoiceRadioList(options, selectedIndex, onSelect, enabled) }
    if (isLiquidGlass) {
        LiquidSegmentedControl(options, selectedIndex, onSelect, modifier, enabled, overflow = radioList)
        return
    }
    // The caller's minimum width reaches the row, so a filling modifier still fills; maxWidth stays the
    // parent's bound, which is what the fit is measured against.
    BoxWithConstraints(modifier = modifier, propagateMinConstraints = true) {
        // M3 measures the label against the segment's full inner width and lets the selected check
        // icon overhang it, so the icon is not part of the budget, and selecting can't flip the layout.
        // Neighbouring segments overlap by a border's width.
        val count = options.size.coerceAtLeast(1)
        val fit = rememberChoiceLabelFit(
            labels = options,
            style = MaterialTheme.typography.labelLarge,
            labelWidth = (maxWidth + SegmentedButtonDefaults.BorderWidth * (count - 1)) / count - SegmentPadding * 2,
            allowTwoLines = false,
        )
        if (!fit.fits) {
            radioList()
            return@BoxWithConstraints
        }
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = enabled,
                ) {
                    Text(label)
                }
            }
        }
    }
}

// M3's segment content padding, each side.
private val SegmentPadding = 12.dp
