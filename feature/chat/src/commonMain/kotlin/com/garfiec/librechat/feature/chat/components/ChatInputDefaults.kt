package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

object ChatInputDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    /** Resting fill shared by the composer input box and the floating top-bar chips. */
    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Resting border shared by the composer input box and the floating top-bar chips. */
    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    val keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.Sentences,
    )

    /** In Liquid Glass the composer's capsule is the frame, so the field draws no fill or border. */
    @Composable
    fun textFieldColors(): TextFieldColors = if (isLiquidGlass) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = borderColor,
        )
    }

    @Composable
    fun toolsButtonColors(): IconButtonColors = if (isLiquidGlass) {
        IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }

    /** In Liquid Glass a bare icon in [glassContentColor]; Material fills it with [containerColor]. */
    @Composable
    fun sendButtonColors(
        containerColor: Color,
        contentColor: Color,
        glassContentColor: Color = MaterialTheme.colorScheme.primary,
    ): IconButtonColors = if (isLiquidGlass) {
        IconButtonDefaults.iconButtonColors(
            contentColor = glassContentColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        )
    } else {
        IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    private const val DISABLED_ALPHA = 0.38f
}
