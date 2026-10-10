package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.glass.LiquidSegmentedControl
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * A single-choice row of text segments: an M3 segmented button row in Material, the iOS segmented
 * control ([LiquidSegmentedControl]) in Liquid Glass. [equalHeightSegments] stretches every Material
 * segment to the row's height, for a row the caller sizes with `height(IntrinsicSize.Max)` so labels
 * that wrap don't leave segments uneven; the iOS control keeps labels to one line.
 */
@Composable
fun AdaptiveSegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    equalHeightSegments: Boolean = false,
) {
    if (isLiquidGlass) {
        LiquidSegmentedControl(options, selectedIndex, onSelect, modifier, enabled)
        return
    }
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = if (equalHeightSegments) Modifier.fillMaxHeight() else Modifier,
                enabled = enabled,
            ) {
                Text(label)
            }
        }
    }
}
