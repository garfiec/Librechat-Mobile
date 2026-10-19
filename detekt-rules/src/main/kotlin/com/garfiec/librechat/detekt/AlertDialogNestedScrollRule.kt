package com.garfiec.librechat.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Flags an `AdaptiveAlertDialog` whose `text` slot scrolls itself but leaves `scrollableText` on.
 * The dialog wraps that slot in its own vertical scroll, which measures the slot with unbounded
 * height: a nested `verticalScroll` or lazy list throws on first measure, and a vertical `weight`
 * child is measured at 0dp. Nothing else catches it; the dialog compiles and crashes when opened.
 *
 * Inside the slot's lambda it reports, unless the call passes `scrollableText = false`:
 * - `verticalScroll(` with no `heightIn(max = …)` or `height(` before it in the same modifier chain;
 * - `LazyColumn`, `LazyVerticalGrid` or `LazyVerticalStaggeredGrid` whose modifier has no such bound;
 * - `.weight(` whose nearest enclosing layout lambda isn't a `Row`/`FlowRow` (fail-closed: a weight in a
 *   lambda the rule can't place on an axis is reported).
 *
 * It is lexical and runs without type resolution: a scroll inside a helper composable the slot calls
 * is not seen, and neither is a `text` slot passed as a variable instead of a lambda.
 */
class AlertDialogNestedScrollRule(config: Config) : Rule(config) {

    override val issue = Issue(
        id = "AlertDialogNestedScroll",
        severity = Severity.Defect,
        description = "An AdaptiveAlertDialog text slot that scrolls itself must pass scrollableText = false.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != DIALOG) return

        val optOut = expression.namedArgument(OPT_OUT)?.getArgumentExpression()?.text
        if (optOut == "false") return
        val slot = expression.namedArgument(TEXT_SLOT)?.getArgumentExpression() as? KtLambdaExpression ?: return

        val offender = slot.collectDescendantsOfType<KtCallExpression>().firstNotNullOfOrNull { it.scrollKind(slot) }
            ?: return
        val message = if (optOut == null || optOut == "true") {
            "The AdaptiveAlertDialog text slot has $offender, which the dialog's own scroll would measure " +
                "unbounded. Pass scrollableText = false, or bound it with heightIn(max = …)."
        } else {
            "The AdaptiveAlertDialog text slot has $offender, and scrollableText = $optOut can't be checked. " +
                "Pass scrollableText = false."
        }
        report(CodeSmell(issue, Entity.from(expression), message))
    }

    /** What makes this call unsafe inside the dialog's scroll, or null when it is safe. */
    private fun KtCallExpression.scrollKind(slot: KtLambdaExpression): String? =
        when (calleeExpression?.text) {
            "verticalScroll" -> "a verticalScroll".takeUnless { boundedBeforeInChain() }
            in LAZY_LISTS -> "an unbounded ${calleeExpression?.text}".takeUnless { modifierIsBounded() }
            "weight" -> "a vertical weight".takeIf { isVerticalWeight(slot) }
            else -> null
        }

    /** True when a height bound comes before this call in its modifier chain, so it applies first. */
    private fun KtCallExpression.boundedBeforeInChain(): Boolean {
        var chain: KtDotQualifiedExpression = parent as? KtDotQualifiedExpression ?: return false
        while (true) chain = chain.parent as? KtDotQualifiedExpression ?: break
        val prefix = chain.text.substring(0, textRange.startOffset - chain.textRange.startOffset)
        return HEIGHT_BOUND.containsMatchIn(prefix)
    }

    private fun KtCallExpression.modifierIsBounded(): Boolean {
        val modifier = namedArgument("modifier")
            ?: valueArguments.firstOrNull { it.getArgumentName() == null && it.getArgumentExpression() !is KtLambdaExpression }
        return modifier?.getArgumentExpression()?.text?.let(HEIGHT_BOUND::containsMatchIn) == true
    }

    /** False only when the nearest layout lambda around the weight is a horizontal one. */
    private fun KtCallExpression.isVerticalWeight(slot: KtLambdaExpression): Boolean {
        var lambda = getStrictParentOfType<KtLambdaExpression>()
        while (lambda != null && lambda != slot) {
            val owner = lambda.getStrictParentOfType<KtCallExpression>()?.calleeExpression?.text
            if (owner in HORIZONTAL_SCOPES) return false
            if (owner in VERTICAL_SCOPES) return true
            lambda = lambda.getStrictParentOfType<KtLambdaExpression>()
        }
        return true
    }

    private fun KtCallExpression.namedArgument(name: String): KtValueArgument? =
        valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == name }

    private companion object {
        const val DIALOG = "AdaptiveAlertDialog"
        const val OPT_OUT = "scrollableText"
        const val TEXT_SLOT = "text"
        val LAZY_LISTS = setOf("LazyColumn", "LazyVerticalGrid", "LazyVerticalStaggeredGrid")
        val HORIZONTAL_SCOPES = setOf("Row", "FlowRow", "LazyRow")
        val VERTICAL_SCOPES = setOf("Column", "FlowColumn", "LazyColumn")

        // heightIn with a max (named, or the second positional), or a fixed height.
        val HEIGHT_BOUND = Regex("""\bheightIn\([^)]*(\bmax\s*=|,)|\b(height|requiredHeight|size)\(""")
    }
}
