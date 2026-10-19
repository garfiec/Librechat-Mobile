package com.garfiec.librechat.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Tests for [AlertDialogNestedScrollRule]. Like the DAO rule's tests, every snippet is linted to PSI
 * without a binding context, the condition the rule runs under in the gate.
 */
class AlertDialogNestedScrollRuleTest {

    private fun lint(slot: String, extraArgs: String = "") = AlertDialogNestedScrollRule(Config.empty).lint(
        """
        fun Screen() {
            AdaptiveAlertDialog(
                onDismissRequest = {},
                confirmButton = {},$extraArgs
                text = {
                    $slot
                },
            )
        }
        """.trimIndent(),
    )

    // region unsafe slots

    @Test
    fun firesOnVerticalScroll() {
        val findings = lint("Column(Modifier.verticalScroll(rememberScrollState())) { Text(\"a\") }")
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("verticalScroll"))
    }

    @Test
    fun firesOnUnboundedLazyColumn() {
        assertEquals(1, lint("LazyColumn(Modifier.fillMaxWidth()) { items(xs) { Text(it) } }").size)
        assertEquals(1, lint("LazyVerticalGrid(columns = GridCells.Fixed(2)) { }").size)
    }

    @Test
    fun firesOnColumnWeight() {
        assertEquals(1, lint("Column { Text(\"a\"); Box(Modifier.weight(1f)) }").size)
    }

    @Test
    fun firesOnWeightInLambdaOfUnknownAxis() {
        assertEquals(1, lint("SettingsCard { Box(Modifier.weight(1f)) }").size)
    }

    @Test
    fun firesWhenBoundComesAfterTheScroll() {
        assertEquals(1, lint("Column(Modifier.verticalScroll(state).heightIn(max = 240.dp)) { }").size)
    }

    @Test
    fun firesOnExplicitTrue() {
        assertEquals(1, lint("Column(Modifier.verticalScroll(state)) { }", "\n        scrollableText = true,").size)
    }

    @Test
    fun firesOnNonLiteralOptOut() {
        val findings = lint("Column(Modifier.verticalScroll(state)) { }", "\n        scrollableText = scrolls,")
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("can't be checked"))
    }

    // endregion

    // region safe slots

    @Test
    fun ignoresOptOut() {
        assertEquals(0, lint("LazyColumn { items(xs) { Text(it) } }", "\n        scrollableText = false,").size)
    }

    @Test
    fun ignoresBoundedLazyColumn() {
        assertEquals(0, lint("LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) { }").size)
        assertEquals(0, lint("LazyColumn(Modifier.height(200.dp)) { }").size)
    }

    @Test
    fun ignoresScrollBoundedBeforeIt() {
        assertEquals(0, lint("Column(Modifier.heightIn(max = 240.dp).verticalScroll(state)) { }").size)
    }

    @Test
    fun ignoresRowWeight() {
        assertEquals(0, lint("Column { Row { Text(\"a\", Modifier.weight(1f)); Text(\"b\") } }").size)
        assertEquals(0, lint("Row { Column(Modifier.weight(1f)) { Text(\"a\") } }").size)
    }

    @Test
    fun ignoresPlainText() {
        assertEquals(0, lint("Text(\"Delete this conversation?\")").size)
    }

    @Test
    fun ignoresOtherDialogs() {
        val findings = AlertDialogNestedScrollRule(Config.empty).lint(
            "fun S() { AlertDialog(onDismissRequest = {}, text = { LazyColumn { } }) }",
        )
        assertEquals(0, findings.size)
    }

    // endregion
}
