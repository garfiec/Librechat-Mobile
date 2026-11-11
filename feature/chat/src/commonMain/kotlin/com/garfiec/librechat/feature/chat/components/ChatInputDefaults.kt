package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.core.ui.theme.chatColors

object ChatInputDefaults {
    /** Resting fill shared by the composer box and the floating top-bar chips. */
    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** Resting border shared by the composer box and the floating top-bar chips. */
    val borderColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    /**
     * Temporary chat's tint, as web tints its composer. A fixed hue in Switchboard rather than a
     * scheme role, so the mode reads the same under any accent or dynamic colour.
     */
    val temporaryAccent: Color
        @Composable get() = chatColors().temporary

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

    /** The field inside the composer box draws no fill or border: the box is its frame, in both styles. */
    @Composable
    fun textFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
    )

    /** 0 → 1 as temporary chat's violet comes in; read it at draw time where possible. */
    @Composable
    fun animateTemporaryAmount(isTemporaryChat: Boolean): State<Float> = animateFloatAsState(
        targetValue = if (isTemporaryChat) 1f else 0f,
        animationSpec = tween(TEMPORARY_TINT_MS),
        label = "temporaryTint",
    )

    /** The box's plain icon buttons ("+", paste, the mic): no fill, in both styles. */
    @Composable
    fun toolsButtonColors(): IconButtonColors =
        IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)

    /**
     * The send slot (Send and the during-run sends) in the theme's [chatColors]: Switchboard's is a bare
     * icon, the box being its frame; a server's is the web's filled circle. A [color] keeps the bare icon.
     */
    @Composable
    fun sendButtonColors(color: Color? = null): IconButtonColors {
        val chat = chatColors()
        return IconButtonDefaults.iconButtonColors(
            containerColor = if (color == null) chat.submit else Color.Transparent,
            contentColor = color ?: chat.onSubmit,
            disabledContainerColor = chat.submitDisabled,
            disabledContentColor = chat.onSubmitDisabled,
        )
    }

    /** Stop shares the send slot: a bare icon in Switchboard's error colour, or a server's filled circle. */
    @Composable
    fun stopButtonColors(): IconButtonColors {
        val chat = chatColors()
        return IconButtonDefaults.iconButtonColors(containerColor = chat.stop, contentColor = chat.onStop)
    }

    private const val TEMPORARY_TINT_MS = 200
    private const val TEMPORARY_BORDER_ALPHA = 0.6f
    private const val TEMPORARY_WASH_ALPHA_LIGHT = 0.08f
    private const val TEMPORARY_WASH_ALPHA_DARK = 0.14f
}
