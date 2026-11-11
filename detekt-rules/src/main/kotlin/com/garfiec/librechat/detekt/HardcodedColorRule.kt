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
 * Flags `Color(0x…)`: a colour written as a hex literal. Outside the theme it paints the same under
 * every theme, so a server theme or the user's accent can't reach it. Read a theme colour instead
 * (`MaterialTheme.colorScheme`, `chatColors()`, `statusColors()`…), or add the colour to a theme
 * producer. `Color.Black` and `Color.White` pass: they are neutrals, not palette choices.
 *
 * The theme directories, tests, and the few colours no theme should own are excluded by path in
 * `config/detekt/detekt.yml`, each with the reason it isn't a theme colour.
 */
class HardcodedColorRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "HardcodedColor",
        severity = Severity.Style,
        description = "A hex colour literal outside the theme doesn't follow the server's or the user's theme.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != "Color") return
        val argument = expression.valueArguments.singleOrNull()?.getArgumentExpression()?.text ?: return
        if (!HEX_LITERAL.matches(argument)) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "$argument paints the same under every theme. Read a theme colour, or move it into a " +
                    "theme producer; a colour no theme should own goes on this rule's allowlist in detekt.yml.",
            ),
        )
    }

    private companion object {
        val HEX_LITERAL = Regex("""0[xX][0-9a-fA-F_]+[uU]?[lL]?(\.toInt\(\))?""")
    }
}
