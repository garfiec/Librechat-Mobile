package com.garfiec.librechat.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.assertEquals
import org.junit.Test

/** Tests for [HardcodedColorRule], linted to PSI without a binding context, as in the gate. */
class HardcodedColorRuleTest {

    private fun lint(expression: String) = HardcodedColorRule(Config.empty).lint("val tint = $expression")

    @Test
    fun firesOnAHexLiteral() {
        assertEquals(1, lint("Color(0xFF4CAF50)").size)
        assertEquals(1, lint("Color(0x8C000000)").size)
        assertEquals(1, lint("Color(0xFF4CAF50.toInt())").size)
    }

    @Test
    fun firesInsideALargerExpression() {
        assertEquals(2, lint("if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)").size)
        assertEquals(1, lint("Brush.linearGradient(listOf(Color(0xFF3A6FF7), scheme.primary))").size)
    }

    @Test
    fun ignoresNeutralsAndThemeReads() {
        assertEquals(0, lint("Color.Black.copy(alpha = 0.55f)").size)
        assertEquals(0, lint("MaterialTheme.colorScheme.primary").size)
    }

    @Test
    fun ignoresAColourBuiltFromAValue() {
        assertEquals(0, lint("Color(0xFF000000.toInt() or rgb)").size)
        assertEquals(0, lint("Color(argb)").size)
        assertEquals(0, lint("Color(red = 0.1f, green = 0.2f, blue = 0.3f)").size)
    }

    @Test
    fun ignoresOtherCallsTakingALiteral() {
        assertEquals(0, lint("canvas.drawColor(0xFFFFFFFF.toInt())").size)
        assertEquals(0, lint("Brush(0xFF000000)").size)
    }
}
