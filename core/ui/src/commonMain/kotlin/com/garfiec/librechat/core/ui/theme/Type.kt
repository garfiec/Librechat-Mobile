package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TCHAT: the full type scale from design/system/tokens.json (`type`). Upstream overrode 7 of the
// 15 M3 styles and left the rest at M3 defaults, so the most-used style (bodySmall, 12sp) was the
// smallest and titleSmall read the same as bodyMedium. Nothing in the app is below 12sp.
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    // TCHAT: paragraphs take their direction from their first strong character, so Persian or
    // Arabic text aligns right in an English UI (and English left in an RTL one), as web's
    // `dir="auto"` does. Unspecified would resolve to the layout direction and left-align it.
    textDirection = TextDirection.Content,
)

val libreChatTypography = Typography(
    // display: empty-state and onboarding heroes, one per screen.
    headlineLarge = style(32, 40, FontWeight.Bold, tracking = -0.01),
    // headline: large screen titles.
    headlineMedium = style(24, 32, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    // title-lg / -md / -sm: bars, dialogs and sheets; section headings; row primary text.
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    // body-lg / -md / -sm: messages and the composer; rows and dialog body; meta and helper text.
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    // label-lg / -md / -sm: buttons; chips, tabs and segments; badges.
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(13, 16, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.Medium, tracking = 0.01),
)
