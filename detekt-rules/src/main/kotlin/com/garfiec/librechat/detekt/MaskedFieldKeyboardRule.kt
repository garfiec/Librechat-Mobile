package com.garfiec.librechat.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags a text field masked with `PasswordVisualTransformation` whose `keyboardOptions` don't ask for
 * the password keyboard. Masking only changes what is drawn: on a text keyboard Android's IME still
 * learns and suggests the value, and iOS secure entry stays off.
 *
 * Lexical, without type resolution: the `keyboardOptions` argument passes when its text names
 * `KeyboardType.Password`, `KeyboardType.NumberPassword` or `MaskedSecretKeyboardOptions`. A mask
 * or options passed through a variable aren't seen.
 */
class MaskedFieldKeyboardRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "MaskedFieldKeyboard",
        severity = Severity.Security,
        description = "A masked text field must use the password keyboard.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val mask = expression.namedArgument("visualTransformation") ?: return
        if (MASK !in mask) return
        val options = expression.namedArgument("keyboardOptions")
        if (options != null && PASSWORD_OPTIONS.containsMatchIn(options)) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "This field is masked but not on the password keyboard, so the IME can learn the value. " +
                    "Use SecretTextField, or pass keyboardOptions with KeyboardType.Password.",
            ),
        )
    }

    private fun KtCallExpression.namedArgument(name: String): String? =
        valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == name }
            ?.getArgumentExpression()?.text

    private companion object {
        const val MASK = "PasswordVisualTransformation"
        val PASSWORD_OPTIONS = Regex("""\bKeyboardType\.(Number)?Password\b|\bMaskedSecretKeyboardOptions\b""")
    }
}
