package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.HueSlider
import com.garfiec.librechat.core.ui.components.SaturationValuePanel
import com.garfiec.librechat.core.ui.components.VerbatimKeyboardOptions
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.hsvColor
import com.garfiec.librechat.core.ui.components.parseHexColor
import com.garfiec.librechat.core.ui.components.toHexString
import com.garfiec.librechat.core.ui.components.toHsv
import com.garfiec.librechat.core.ui.theme.AccentColorPresets
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.accent_color
import com.garfiec.librechat.feature.settings.resources.accent_color_custom
import com.garfiec.librechat.feature.settings.resources.accent_color_hex
import com.garfiec.librechat.feature.settings.resources.accent_name_amber
import com.garfiec.librechat.feature.settings.resources.accent_name_blue
import com.garfiec.librechat.feature.settings.resources.accent_name_cyan
import com.garfiec.librechat.feature.settings.resources.accent_name_green
import com.garfiec.librechat.feature.settings.resources.accent_name_indigo
import com.garfiec.librechat.feature.settings.resources.accent_name_lavender
import com.garfiec.librechat.feature.settings.resources.accent_name_orange
import com.garfiec.librechat.feature.settings.resources.accent_name_pink
import com.garfiec.librechat.feature.settings.resources.accent_name_red
import com.garfiec.librechat.feature.settings.resources.accent_name_slate
import com.garfiec.librechat.feature.settings.resources.accent_name_turquoise
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_save
import org.jetbrains.compose.resources.stringResource

/**
 * Accent color picker dialog: a grid of curated preset swatches plus a custom HSV
 * picker with hex input. HSV is the single source of truth; swatches and the hex
 * field seed it. [onColorSelect] receives the chosen ARGB int on Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentColorDialog(
    currentColor: Int,
    onColorSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown above the swatches, e.g. that the server's accent is in use until its switch is off. */
    note: String? = null,
) {
    val initialHsv = remember(currentColor) { Color(currentColor).toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var value by remember { mutableFloatStateOf(initialHsv.third) }
    var hexText by remember { mutableStateOf(Color(currentColor).toHexString()) }

    val working = hsvColor(hue, saturation, value)
    val workingArgb = working.toArgb()

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        value = v
        hexText = hsvColor(h, s, v).toHexString()
    }

    AdaptiveAlertDialog(
        modifier = modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        scrollableText = false,
        title = { Text(stringResource(Res.string.accent_color)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentColorPresets.forEachIndexed { index, preset ->
                        val selected = preset.toArgb() == workingArgb
                        val name = AccentPresetNames.getOrNull(index)?.let { stringResource(it) } ?: preset.toHexString()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .semantics { contentDescription = name }
                                .clip(CircleShape)
                                .background(preset)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    val hsv = preset.toHsv()
                                    applyHsv(hsv.first, hsv.second, hsv.third)
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(Res.string.accent_color_custom),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v -> applyHsv(hue, s, v) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                HueSlider(
                    hue = hue,
                    onHueChange = { h -> applyHsv(h, saturation, value) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                AdaptiveOutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        parseHexColor(input)?.let { parsed ->
                            val hsv = parsed.toHsv()
                            hue = hsv.first
                            saturation = hsv.second
                            value = hsv.third
                        }
                    },
                    label = { Text(stringResource(Res.string.accent_color_hex)) },
                    singleLine = true,
                    keyboardOptions = VerbatimKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = { onColorSelect(working.toArgb()) }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/** Spoken names for [AccentColorPresets], in its order; a preset without one is read as its hex. */
private val AccentPresetNames = listOf(
    Res.string.accent_name_blue,
    Res.string.accent_name_indigo,
    Res.string.accent_name_lavender,
    Res.string.accent_name_cyan,
    Res.string.accent_name_turquoise,
    Res.string.accent_name_green,
    Res.string.accent_name_amber,
    Res.string.accent_name_orange,
    Res.string.accent_name_red,
    Res.string.accent_name_pink,
    Res.string.accent_name_slate,
)
