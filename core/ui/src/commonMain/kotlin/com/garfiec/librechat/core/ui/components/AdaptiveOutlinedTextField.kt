package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * In Liquid Glass, the iOS rounded field (M3's filled [TextField] without its indicator line).
 * [shape] and [colors] only apply in Material.
 */
@Composable
fun AdaptiveOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    // TCHAT: the label sits above the box (see [LabeledField]).
    LabeledField(label = label, isError = isError, enabled = enabled, modifier = modifier) { fieldModifier ->
        if (!isLiquidGlass) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange, modifier = fieldModifier, enabled = enabled,
                readOnly = readOnly, textStyle = textStyle, label = null, placeholder = placeholder,
                leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
                supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
                maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = shape,
                colors = colors,
            )
            return@LabeledField
        }
        TextField(
            value = value, onValueChange = onValueChange, modifier = fieldModifier, enabled = enabled,
            readOnly = readOnly, textStyle = textStyle, label = null, placeholder = placeholder,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = GlassFieldShape,
            colors = glassFieldColors(colors),
        )
    }
}

/** The [TextFieldValue] overload of [AdaptiveOutlinedTextField]; see there. */
@Composable
fun AdaptiveOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    // TCHAT: the label sits above the box (see [LabeledField]).
    LabeledField(label = label, isError = isError, enabled = enabled, modifier = modifier) { fieldModifier ->
        if (!isLiquidGlass) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange, modifier = fieldModifier, enabled = enabled,
                readOnly = readOnly, textStyle = textStyle, label = null, placeholder = placeholder,
                leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
                supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
                maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = shape,
                colors = colors,
            )
            return@LabeledField
        }
        TextField(
            value = value, onValueChange = onValueChange, modifier = fieldModifier, enabled = enabled,
            readOnly = readOnly, textStyle = textStyle, label = null, placeholder = placeholder,
            leadingIcon = leadingIcon, trailingIcon = trailingIcon, prefix = prefix, suffix = suffix,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
            maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = GlassFieldShape,
            colors = glassFieldColors(colors),
        )
    }
}

/** The [TextFieldState] overload of [AdaptiveOutlinedTextField]; see there. */
@Composable
fun AdaptiveOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    if (!isLiquidGlass) {
        OutlinedTextField(
            labelPosition = TextFieldLabelPosition.Above(), // TCHAT: label above the box
            state = state, modifier = modifier, enabled = enabled, readOnly = readOnly, textStyle = textStyle,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
            inputTransformation = inputTransformation, outputTransformation = outputTransformation,
            keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction, lineLimits = lineLimits,
            interactionSource = interactionSource, shape = shape, colors = colors,
        )
        return
    }
    TextField(
        labelPosition = TextFieldLabelPosition.Above(), // TCHAT: label above the box
        state = state, modifier = modifier, enabled = enabled, readOnly = readOnly, textStyle = textStyle,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
        inputTransformation = inputTransformation, outputTransformation = outputTransformation,
        keyboardOptions = keyboardOptions, onKeyboardAction = onKeyboardAction, lineLimits = lineLimits,
        interactionSource = interactionSource, shape = GlassFieldShape, colors = glassFieldColors(colors),
    )
}

/**
 * Borderless in every state (iOS shows focus with the caret). Text, label and icon colours are kept,
 * so a caller that styles a disabled field to read as enabled still gets that.
 */
@Composable
private fun glassFieldColors(colors: TextFieldColors): TextFieldColors {
    val fill = GlassControlColors.fill
    return colors.copy(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = fill,
        errorContainerColor = fill,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )
}

private val GlassFieldShape = RoundedCornerShape(12.dp) // TCHAT: radius-md, inputs (was 10)

/**
 * TCHAT: the design's TextField anatomy for the value overloads, whose M3 fields can't place the
 * label above the box: the label above in `label-md`, `ink-muted` (`danger` on error), the field
 * filling the width below. The caller's modifier lands on the column; `focusRequester` there still
 * focuses the field, since a requester on a parent focuses its first focusable child.
 */
// The field slot runs in exactly one branch per composition; the label decides which, so it never moves.
@Suppress("ContentSlotReused")
@Composable
private fun LabeledField(
    label: @Composable (() -> Unit)?,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    field: @Composable (Modifier) -> Unit,
) {
    if (label == null) {
        field(modifier)
        return
    }
    val colors = MaterialTheme.colorScheme
    val labelColor = when {
        isError -> colors.error
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        else -> colors.onSurfaceVariant
    }
    Column(modifier = modifier) {
        CompositionLocalProvider(LocalContentColor provides labelColor) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) { label() }
        }
        Spacer(Modifier.height(4.dp))
        field(Modifier.fillMaxWidth())
    }
}
