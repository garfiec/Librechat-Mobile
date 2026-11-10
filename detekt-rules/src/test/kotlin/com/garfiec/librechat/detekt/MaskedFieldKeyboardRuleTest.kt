package com.garfiec.librechat.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.assertEquals
import org.junit.Test

/** Tests for [MaskedFieldKeyboardRule], linted to PSI without a binding context, as in the gate. */
class MaskedFieldKeyboardRuleTest {

    private fun lint(args: String) = MaskedFieldKeyboardRule(Config.empty).lint(
        """
        fun Screen() {
            AdaptiveOutlinedTextField(
                value = value,
                onValueChange = {},
                $args
            )
        }
        """.trimIndent(),
    )

    @Test
    fun firesOnMaskWithoutKeyboardOptions() {
        assertEquals(1, lint("visualTransformation = PasswordVisualTransformation(),").size)
    }

    @Test
    fun firesOnConditionalMaskWithoutKeyboardOptions() {
        assertEquals(
            1,
            lint("visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),").size,
        )
    }

    @Test
    fun firesOnMaskWithNonPasswordKeyboard() {
        assertEquals(
            1,
            lint("visualTransformation = PasswordVisualTransformation(),\nkeyboardOptions = VerbatimKeyboardOptions,").size,
        )
    }

    @Test
    fun ignoresMaskWithPasswordKeyboard() {
        assertEquals(
            0,
            lint(
                "visualTransformation = PasswordVisualTransformation(),\n" +
                    "keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),",
            ).size,
        )
    }

    @Test
    fun ignoresMaskWithNumberPasswordKeyboard() {
        assertEquals(
            0,
            lint(
                "visualTransformation = PasswordVisualTransformation(),\n" +
                    "keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),",
            ).size,
        )
    }

    @Test
    fun ignoresMaskWithSharedSecretOptions() {
        assertEquals(
            0,
            lint(
                "visualTransformation = PasswordVisualTransformation(),\n" +
                    "keyboardOptions = MaskedSecretKeyboardOptions.copy(imeAction = imeAction),",
            ).size,
        )
    }

    @Test
    fun ignoresUnmaskedField() {
        assertEquals(0, lint("label = { Text(\"Name\") },").size)
    }
}
