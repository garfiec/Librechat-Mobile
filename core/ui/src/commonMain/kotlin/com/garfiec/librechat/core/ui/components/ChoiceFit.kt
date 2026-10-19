package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What an equal-cell choice control does when a label doesn't fit its cell on one line. */
enum class ChoiceFallback {
    /** Stack the choice as radio rows: for a setting, where the list's height costs little. */
    RADIO_LIST,

    /**
     * Never stack: wrap labels onto a second line where that fits without breaking a word, else cut
     * them short with an ellipsis. For a strip that switches what's shown below it, which a list would push
     * down the screen.
     */
    TWO_LINES,
}

/**
 * How labels sit in their cells: whether they all [fits], on how many [lines] (two only when allowed, one
 * wasn't enough and no word breaks), and the tallest one's [textHeight] at that many lines.
 */
internal class ChoiceLabelFit(val fits: Boolean, val lines: Int, val textHeight: Dp)

/**
 * Whether every label fits a cell [labelWidth] wide in [style]: on one line, or on two when
 * [allowTwoLines], without breaking a word. A Latin word wider than the cell would break mid-word, so
 * each one has to fit a line; CJK text breaks between any two characters. The answer depends on the
 * labels and the width alone, so selecting an option can't flip it.
 */
@Composable
internal fun rememberChoiceLabelFit(
    labels: List<String>,
    style: TextStyle,
    labelWidth: Dp,
    allowTwoLines: Boolean,
): ChoiceLabelFit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, labelWidth, allowTwoLines, measurer, density) {
        val widthPx = with(density) { labelWidth.roundToPx() }.coerceAtLeast(0)

        // Laid out at the cell's width, as the label will be, rather than compared by rounded widths.
        fun fitsOneLine(text: String) = measurer.fitsIn(text, style, widthPx, lines = 1)
        fun fit(fits: Boolean, lines: Int): ChoiceLabelFit {
            val tallest = labels.maxOfOrNull {
                measurer.measure(it, style, maxLines = lines, constraints = Constraints(maxWidth = widthPx)).size.height
            } ?: 0
            return ChoiceLabelFit(fits, lines, with(density) { tallest.toDp() })
        }
        when {
            labels.all { fitsOneLine(it) } -> fit(fits = true, lines = 1)
            !allowTwoLines -> fit(fits = false, lines = 1)
            labels.any { label -> latinWords(label).any { !fitsOneLine(it) } } -> fit(fits = false, lines = 1)
            else -> fit(fits = labels.all { measurer.fitsIn(it, style, widthPx, lines = 2) }, lines = 2)
        }
    }
}

private fun TextMeasurer.fitsIn(text: String, style: TextStyle, widthPx: Int, lines: Int): Boolean =
    !measure(text, style, maxLines = lines, constraints = Constraints(maxWidth = widthPx)).hasVisualOverflow

/**
 * The words of [label] that only break at spaces: those with no CJK character. Hangul counts as CJK,
 * as the line breaker splits it between syllables; an emoji (a surrogate pair) doesn't.
 */
internal fun latinWords(label: String): List<String> =
    label.split(' ').filter { word -> word.isNotEmpty() && word.none { it.code >= CJK_START && !it.isSurrogate() } }

// CJK Radicals Supplement: from here on, scripts break between characters.
private const val CJK_START = 0x2E80

/**
 * An equal-cell choice that didn't fit, stacked as radio rows: the same options, selection and
 * enabled state, in the list form a settings dialog uses.
 */
@Composable
internal fun ChoiceRadioList(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = index == selectedIndex,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveRadioButton(selected = index == selectedIndex, onClick = null, enabled = enabled)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
                )
            }
        }
    }
}

// M3's disabled-content alpha, shared by the Material choice controls.
internal const val DISABLED_CONTENT_ALPHA = 0.38f
