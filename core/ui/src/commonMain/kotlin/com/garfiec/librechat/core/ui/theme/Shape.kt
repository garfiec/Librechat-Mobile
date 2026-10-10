package com.garfiec.librechat.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// TCHAT: the six radius steps from design/system/tokens.json (`radius`). Upstream: small 8,
// medium 16, large 24, extraLarge 28.
val libreChatShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // radius-xs: inline code, badges
    small = RoundedCornerShape(8.dp), // radius-sm: chips, small buttons, code blocks
    medium = RoundedCornerShape(12.dp), // radius-md: inputs, cards, cells, menus, banners
    large = RoundedCornerShape(20.dp), // radius-lg: bubbles, the composer
    extraLarge = RoundedCornerShape(28.dp), // radius-xl: dialogs, sheet tops
)
