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

/**
 * The last items, the last rows drawn (items plus the removed ones still shrinking out), and the
 * leaving set. A leaving row keeps its slot among the rows drawn last time, so it stays put while
 * another is removed around it.
 */
private class RowsMemory<T>(private var last: List<T>) {
    var opened = false
    val leaving = mutableSetOf<T>()
    private var lastShown: List<T> = last

    fun shown(items: List<T>): List<T> {
        last.forEach { item -> if (item !in items) leaving.add(item) }
        leaving.removeAll(items.toSet())
        last = items
        if (leaving.isEmpty()) {
            lastShown = items
            return items
        }
        // Rows that finished shrinking out are gone from both, so they don't shift the slots.
        val previous = lastShown.filter { it in leaving || it in items }
        val out = items.toMutableList()
        previous.forEachIndexed { index, item -> if (item in leaving) out.add(index.coerceAtMost(out.size), item) }
        lastShown = out
        return out
    }
}

private const val ROW_MILLIS = 200
