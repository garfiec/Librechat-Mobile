package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

/**
 * Discrete slider that maps a list of string [options] to slider positions.
 * Matches upstream's `enum`-typed Slider component (e.g. reasoning_effort,
 * verbosity, imageDetail) where the value is one of a fixed string set but
 * the UI affordance is a slider rather than a dropdown.
 *
 * [selectedValue] is the current string option; [optionLabels] overrides the
 * label shown above the slider when it differs from the raw option (e.g.
 * "none" → "Unset"). [removedValues] are the values a per-model rule took away
 * from this model (see [EndpointParameterRegistry.modelRemovedOptions]); they
 * are the only out-of-list values that read as the first option.
 */
@Composable
fun DynamicEnumSlider(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    optionLabels: Map<String, String>? = null,
    removedValues: Set<String> = emptySet(),
) {
    if (options.isEmpty()) return

    // A value outside the options has no position; the thumb rests at the first one.
    val index = options.indexOf(selectedValue).let { if (it < 0) 0 else it }
    val displayLabel = enumSliderLabel(selectedValue, options, optionLabels, removedValues)
    val sliderCd = "$label slider, value $displayLabel"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = sliderCd },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { newPos ->
                val newIndex = newPos.roundToInt().coerceIn(0, options.lastIndex)
                onValueChange(options[newIndex])
            },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What the slider's value label says for [selectedValue].
 *
 * A value in [options] is named by its option. One outside them is named by the first option
 * ("Unset") only when that is what the request does with it: a value a per-model rule took away
 * ([removedValues]) is not sent (ModelParamPayload), so its own name would describe a setting with
 * no effect. Any other out-of-list value — `xhigh`/`max` before the server version is detected, or
 * one newer than this app — IS sent, so it is named as itself; "Unset" there would be a lie.
 */
internal fun enumSliderLabel(
    selectedValue: String,
    options: List<String>,
    optionLabels: Map<String, String>?,
    removedValues: Set<String>,
): String {
    val sentAsItself = selectedValue.isNotEmpty() && selectedValue !in options && selectedValue !in removedValues
    val shown = if (sentAsItself || selectedValue in options) selectedValue else options.firstOrNull().orEmpty()
    // The empty option is "not set"; named as DynamicDropdown names it, so the two controls agree.
    return optionLabels?.get(shown) ?: shown.ifEmpty { "Unset" }
}
