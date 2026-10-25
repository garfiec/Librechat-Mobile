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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

object ChatInputDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    /** Resting fill shared by the composer input box and the floating top-bar chips. */
    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Resting border shared by the composer input box and the floating top-bar chips. */
    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    /**
     * Temporary chat's violet, as web tints its composer. A fixed hue rather than a scheme role, so
     * the mode reads the same under any accent or dynamic colour.
     */
    val temporaryAccent: Color
        @Composable get() = if (LocalDarkTheme.current) TemporaryVioletDark else TemporaryVioletLight

    /** The composer's resting border while temporary chat is on. */
    val temporaryBorderColor: Color
        @Composable get() = temporaryAccent.copy(alpha = TEMPORARY_BORDER_ALPHA)

    /** A violet wash laid over the composer's fill (or the glass capsule) while temporary chat is on. */
    val temporaryWash: Color
        @Composable get() = temporaryAccent.copy(
            alpha = if (LocalDarkTheme.current) TEMPORARY_WASH_ALPHA_DARK else TEMPORARY_WASH_ALPHA_LIGHT,
        )

    val keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = ImeAction.Default,
        capitalization = KeyboardCapitalization.Sentences,
    )

    /** In Liquid Glass the composer's capsule is the frame, so the field draws no fill or border. */
    @Composable
    fun textFieldColors(isTemporaryChat: Boolean = false): TextFieldColors = if (isLiquidGlass) {
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        )
    } else if (isTemporaryChat) {
        val fill = temporaryWash.compositeOver(containerColor)
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            focusedBorderColor = temporaryAccent,
            unfocusedBorderColor = temporaryBorderColor,
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
    private const val TEMPORARY_BORDER_ALPHA = 0.6f
    private const val TEMPORARY_WASH_ALPHA_LIGHT = 0.08f
    private const val TEMPORARY_WASH_ALPHA_DARK = 0.14f

    // Tailwind violet-600 / violet-400: web's violet, stepped for contrast against each surface.
    private val TemporaryVioletLight = Color(0xFF7C3AED)
    private val TemporaryVioletDark = Color(0xFFA78BFA)
}
