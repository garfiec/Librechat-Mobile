package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice

/**
 * Tells whether the user changed the shown model or parameters while something was out (the
 * conversation load's backfill fetch). The seeder's config rewrites (a spec the config stopped
 * offering, an enforced preset) are not the user's: a watch follows them.
 */
internal class SelectionWatches(private val shown: () -> Pair<ModelChoice, ModelParameters>) {

    private class Watch(var shown: Pair<ModelChoice, ModelParameters>)

    private val watches = mutableListOf<Watch>()

    /** Starts a watch. The returned check is true while nothing but a config rewrite changed the selection; call it once. */
    fun watch(): () -> Boolean {
        val watch = Watch(shown())
        watches += watch
        return {
            watches.remove(watch)
            watch.shown == shown()
        }
    }

    /** Runs the seeder's config [rewrite]; the watches that still saw the selection before it follow it. */
    fun configRewrite(rewrite: () -> Unit) {
        if (watches.isEmpty()) return rewrite()
        val before = shown()
        rewrite()
        val after = shown()
        watches.filter { it.shown == before }.forEach { it.shown = after }
    }
}
