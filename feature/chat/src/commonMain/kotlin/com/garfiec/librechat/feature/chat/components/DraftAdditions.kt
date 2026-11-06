package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.garfiec.librechat.feature.chat.util.DraftItems

/**
 * What the attachment stack pops for and announces: the item last added to the draft. Kept above
 * the stack, which leaves composition while the draft is empty, so the first item added counts as
 * an addition too. What the draft held when this was created (a draft restored with the
 * conversation) is not one.
 */
@Stable
class DraftAdditions internal constructor(initialKeys: Set<String>) {
    private var lastKeys: Set<String> = initialKeys
    private var poppedSeq = 0

    /** The newest addition's label while the last change was an addition; null after a removal. */
    var lastAdded: String? by mutableStateOf(null)
        private set

    /** Counts additions, so the stack pops once per addition. */
    var seq: Int by mutableIntStateOf(0)
        private set

    internal fun update(items: DraftItems, keys: Set<String> = items.keySet()) {
        // A retry or an upload's outcome changes the items, not what is in the draft.
        if (keys == lastKeys) return
        // Found by key: the list is sorted (failed first, quotes last), so the newest item isn't last.
        val added = items.items.firstOrNull { it.key !in lastKeys }
        val grew = keys.size > lastKeys.size
        lastKeys = keys
        if (added != null && grew) {
            lastAdded = added.label()
            seq++
        } else {
            lastAdded = null
        }
    }

    /**
     * True once per addition, for the stack's pop: a stack that comes back into composition (the
     * composer hidden by a question and shown again) doesn't pop for an old one.
     */
    internal fun takePop(): Boolean {
        if (lastAdded == null || poppedSeq == seq) return false
        poppedSeq = seq
        return true
    }
}

/**
 * A [DraftAdditions] following [items], for the screen that owns the draft. It runs when what is in
 * the draft changes (its keys), not on every change to the items.
 */
@Composable
internal fun rememberDraftAdditions(items: DraftItems): DraftAdditions {
    val keys = remember(items) { items.keySet() }
    val additions = remember { DraftAdditions(keys) }
    val current by rememberUpdatedState(items)
    LaunchedEffect(keys) { additions.update(current, keys) }
    return additions
}

private fun DraftItems.keySet(): Set<String> = items.mapTo(HashSet()) { it.key }
