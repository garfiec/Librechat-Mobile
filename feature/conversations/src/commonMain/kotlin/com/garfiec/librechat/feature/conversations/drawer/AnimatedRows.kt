package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Rows of [items], keyed by item, that grow in when one is added and shrink out where it was when
 * one is removed, so the rows around it slide instead of jumping. Rows there on the first
 * composition don't animate in. [itemModifier] goes on each row's animated wrapper: put what must
 * order or move the row among its siblings there (`zIndex`, a drag's translation), not on the row.
 */
@Composable
internal fun <T> AnimatedRows(
    items: List<T>,
    itemModifier: (T) -> Modifier = { Modifier },
    content: @Composable (T) -> Unit,
) {
    val memory = remember { RowsMemory(items) }
    SideEffect { memory.opened = true }
    memory.shown(items).forEach { item ->
        key(item) {
            val state = remember { MutableTransitionState(!memory.opened) }
            state.targetState = item in items
            if (state.isIdle && !state.currentState) SideEffect { memory.leaving.remove(item) }
            AnimatedVisibility(
                visibleState = state,
                modifier = itemModifier(item),
                enter = expandVertically(tween(ROW_MILLIS)) + fadeIn(tween(ROW_MILLIS)),
                exit = shrinkVertically(tween(ROW_MILLIS)) + fadeOut(tween(ROW_MILLIS)),
            ) {
                content(item)
            }
        }
    }
}

/** The last items drawn, and the removed ones still shrinking out at the index they had. */
private class RowsMemory<T>(private var last: List<T>) {
    var opened = false
    val leaving = mutableMapOf<T, Int>()

    fun shown(items: List<T>): List<T> {
        last.forEachIndexed { index, item -> if (item !in items) leaving[item] = index }
        items.forEach { leaving.remove(it) }
        last = items
        if (leaving.isEmpty()) return items
        val out = items.toMutableList()
        leaving.entries.sortedBy { it.value }.forEach { (item, index) -> out.add(index.coerceAtMost(out.size), item) }
        return out
    }
}

private const val ROW_MILLIS = 200
