package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos

/**
 * Hands a collapsible's open state from the live reply to the saved message. They are different
 * list items, so state remembered inside the live one is dropped when the reply finishes: an
 * expanded list would snap shut. The live side records each toggle here; the saved side takes
 * its entry once, when it first composes.
 *
 * Every key is scoped by [LocalExpansionScope] to one reply in one pane, so a card in another turn
 * (some providers number tool calls per message) or in the other comparison pane never matches.
 * [ClearExpansionStatesAfterReply] empties a pane's entries once its reply has settled, so nothing outlives
 * it — including the entries of a reply that errored or was stopped.
 */
@Stable
internal class ExpansionStates(initial: Map<String, Boolean> = emptyMap()) {
    // Not snapshot state: nothing observes it, and both sides use it while composing.
    private val pending = HashMap(initial)

    fun record(key: String, expanded: Boolean) {
        pending[key] = expanded
    }

    fun take(key: String): Boolean? = pending.remove(key)

    /** Drops every entry of [pane], whose reply has settled; the other pane may still be streaming. */
    fun clear(pane: ExpansionPane) {
        pending.keys.removeAll { it.startsWith("${pane.name}/") }
    }

    companion object {
        // Two parallel lists: a Bundle holds lists of strings and booleans, not a Map or a Pair.
        val Saver: Saver<ExpansionStates, Any> = listSaver(
            save = { listOf(ArrayList(it.pending.keys), ArrayList(it.pending.values)) },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val keys = saved[0] as List<String>

                @Suppress("UNCHECKED_CAST")
                val values = saved[1] as List<Boolean>
                ExpansionStates(keys.zip(values).toMap())
            },
        )
    }
}

/** The chat screen's [ExpansionStates], provided by [ChatRoot]. Null outside it (previews, tests). */
internal val LocalExpansionStates = staticCompositionLocalOf<ExpansionStates?> { null }

/**
 * The reply and pane a carried collapsible belongs to, from [expansionScope]. The live reply and
 * its saved message must provide the same value; blank (the default) carries nothing.
 */
internal val LocalExpansionScope = staticCompositionLocalOf { "" }

internal enum class ExpansionPane { PRIMARY, SECONDARY }

/**
 * A reply's scope, named by the message it answers: the live reply attaches to [parentMessageId]
 * and its saved message carries it as its parent, so both sides can name it.
 */
internal fun expansionScope(pane: ExpansionPane, parentMessageId: String?): String =
    parentMessageId?.takeIf(String::isNotEmpty)?.let { "${pane.name}/$it" }.orEmpty()

@Composable
internal fun rememberExpansionStates(): ExpansionStates =
    rememberSaveable(saver = ExpansionStates.Saver) { ExpansionStates() }

/**
 * Empties [pane]'s entries in [LocalExpansionStates] a frame after its reply stops streaming. The
 * saved message takes its entries in the frame it replaces the live reply, so by the next one
 * anything left belongs to nothing on screen.
 */
@Composable
internal fun ClearExpansionStatesAfterReply(isStreaming: Boolean, pane: ExpansionPane) {
    val store = LocalExpansionStates.current ?: return
    LaunchedEffect(store, isStreaming, pane) {
        if (!isStreaming) {
            withFrameNanos { }
            store.clear(pane)
        }
    }
}

/**
 * Whether the collapsible [kind] of the thing identified by [id] is open. Saveable as usual; in
 * addition a [live] collapsible keeps its state in [LocalExpansionStates], and a saved one starts
 * from the state its live counterpart handed over. A blank [id] or scope identifies nothing across
 * the hand-off, so it stays local.
 */
@Composable
internal fun rememberCarriedExpansion(
    kind: String,
    id: String,
    live: Boolean,
    initial: Boolean = false,
): MutableState<Boolean> {
    val scope = LocalExpansionScope.current
    val carried = id.isNotBlank() && scope.isNotEmpty()
    val key = "$scope|$kind:$id"
    val store = LocalExpansionStates.current?.takeIf { carried }
    // A blank id would share one saveable key with every other blank-id collapsible in the item.
    val local = rememberSaveable(key = "$kind:$id".takeIf { id.isNotBlank() }) {
        mutableStateOf(if (store != null && !live) store.take(key) ?: initial else initial)
    }
    if (store == null || !live) return local
    return remember(local, store, key) { RecordedExpansion(local, store, key) }
}

private class RecordedExpansion(
    private val local: MutableState<Boolean>,
    private val store: ExpansionStates,
    private val key: String,
) : MutableState<Boolean> {
    override var value: Boolean
        get() = local.value
        set(expanded) {
            local.value = expanded
            store.record(key, expanded)
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { value = it }
}
