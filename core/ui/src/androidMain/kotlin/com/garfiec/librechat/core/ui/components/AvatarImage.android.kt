package com.garfiec.librechat.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android backward-compatible overload that accepts a drawable resource ID.
 * Feature modules not yet migrated to KMP can continue using `@DrawableRes Int?`.
 */
@Composable
fun AvatarImage(
    imageUrl: String?,
    @DrawableRes fallbackIconRes: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fallbackText: String = "?",
    fallbackBackgroundColor: Color? = null,
    showPersonIcon: Boolean = false,
    tintIcon: Boolean = false,
    contentDescription: String? = "$fallbackText avatar",
) {
    AvatarImage(
        imageUrl = imageUrl,
        modifier = modifier,
        size = size,
        fallbackText = fallbackText,
        fallbackIconPainter = fallbackIconRes?.let { painterResource(it) },
        fallbackBackgroundColor = fallbackBackgroundColor,
        showPersonIcon = showPersonIcon,
        tintIcon = tintIcon,
        contentDescription = contentDescription,
    )
}
