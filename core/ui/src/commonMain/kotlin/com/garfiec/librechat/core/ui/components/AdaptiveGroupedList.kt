package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/** Where a row sits in a grouped section; decides which corners round and whether a separator follows. */
enum class GroupPosition {
    FIRST,
    MIDDLE,
    LAST,
    ONLY,
    ;

    val isLast: Boolean get() = this == LAST || this == ONLY
}

/** The position of the grouped cell being composed, or null outside a grouped section (and in Material). */
internal val LocalGroupPosition = staticCompositionLocalOf<GroupPosition?> { null }

class AdaptiveSectionScope internal constructor() {
    internal val rows = mutableListOf<Pair<Any, @Composable () -> Unit>>()

    fun row(key: Any, content: @Composable () -> Unit) {
        rows += key to content
    }
}

/** One lazy item per row. Positions are computed here so conditional rows can't leave a stray corner. */
fun LazyListScope.adaptiveSection(content: AdaptiveSectionScope.() -> Unit) {
    val rows = AdaptiveSectionScope().apply(content).rows
    rows.forEachIndexed { index, (key, row) ->
        val position = when {
            rows.size == 1 -> GroupPosition.ONLY
            index == 0 -> GroupPosition.FIRST
            index == rows.lastIndex -> GroupPosition.LAST
            else -> GroupPosition.MIDDLE
        }
        item(key = key) { AdaptiveGroupedCell(position) { row() } }
    }
}

@Composable
internal fun AdaptiveGroupedCell(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val glass = isLiquidGlass
    val cell = if (glass) {
        Modifier
            .padding(horizontal = GroupInset)
            .clip(position.shape)
            .background(GlassControlColors.cell)
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalGroupPosition provides position.takeIf { glass }) {
        // A Column, not a Box: a lazy item stacks several root children vertically, and so must its cell.
        Column(modifier = modifier.then(cell)) { content() }
    }
}

/**
 * The background for a row's own `Surface`: transparent inside a Liquid Glass grouped cell, so the
 * cell colour shows through, and the M3 surface default everywhere else.
 */
val adaptiveRowColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalGroupPosition.current != null) Color.Transparent else MaterialTheme.colorScheme.surface

/** A page of grouped sections: the grouped-background colour in Liquid Glass, nothing in Material. */
@Composable
fun AdaptiveGroupedPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = if (isLiquidGlass) {
        Modifier.background(GlassControlColors.groupedBackground)
    } else {
        Modifier
    }
    Box(modifier.then(background)) { content() }
}

/**
 * The title above an [adaptiveSection]: an accent `titleSmall` in Material, the iOS grouped header
 * (small, upper-case, secondary colour, aligned with the cell text) in Liquid Glass.
 */
@Composable
fun AdaptiveSectionHeader(title: String, modifier: Modifier = Modifier) {
    if (isLiquidGlass) {
        GlassSectionHeader(title, modifier)
        return
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = title.uppercase(),
        style = MaterialTheme.typography.bodySmall.copy(color = GlassControlColors.secondaryLabel),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = GroupInset * 2, end = GroupInset * 2, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

private val GroupInset = 16.dp
private val GroupRadius = 12.dp
private val FirstShape = RoundedCornerShape(topStart = GroupRadius, topEnd = GroupRadius)
private val LastShape = RoundedCornerShape(bottomStart = GroupRadius, bottomEnd = GroupRadius)
private val OnlyShape = RoundedCornerShape(GroupRadius)
private val MiddleShape = RoundedCornerShape(0.dp)

private val GroupPosition.shape
    get() = when (this) {
        GroupPosition.FIRST -> FirstShape
        GroupPosition.MIDDLE -> MiddleShape
        GroupPosition.LAST -> LastShape
        GroupPosition.ONLY -> OnlyShape
    }
