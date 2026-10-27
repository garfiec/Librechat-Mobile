package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.cd_hide_secret
import com.garfiec.librechat.core.ui.resources.cd_show_secret
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A field for a secret that isn't the user's login (an API key, a gateway token, a PDF password):
 * masked unless [revealed], with a trailing show/hide button, kept out of autofill and off the IME's
 * learning and suggestions.
 *
 * Reveal is hoisted so the caller can re-mask on ON_PAUSE and knows when a secret is on screen (e.g.
 * to make its sheet `secure`). ON_PAUSE rather than ON_STOP because of iOS: its stop comes when the
 * scene enters the background, after Compose has stopped drawing, so a re-mask then never reaches
 * the app-switcher snapshot.
 *
 * [showDescription] and [hideDescription] name the button for a screen reader; [revealButtonModifier]
 * goes on the button (a test tag), [modifier] on the field.
 */
@Composable
fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    revealButtonModifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    showDescription: StringResource = Res.string.cd_show_secret,
    hideDescription: StringResource = Res.string.cd_hide_secret,
) {
    AdaptiveOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.excludeFromAutofill(),
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        // Keeps the IME from learning, suggesting or autocorrecting the secret.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        trailingIcon = {
            IconButton(
                onClick = { onRevealedChange(!revealed) },
                enabled = enabled,
                modifier = revealButtonModifier,
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (revealed) hideDescription else showDescription),
                )
            }
        },
    )
}
