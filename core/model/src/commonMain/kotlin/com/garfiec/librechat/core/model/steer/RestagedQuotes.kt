package com.garfiec.librechat.core.model.steer

/**
 * Max excerpts staged at once. MIRRORED from upstream `MAX_QUOTE_COUNT`, which itself mirrors the
 * backend's `QUOTE_MAX_COUNT`, so every displayed chip actually reaches the model on the next send.
 */
const val MAX_QUOTE_COUNT = 10

/**
 * Dedupe-appends re-staged excerpts onto the composer's pending-quote chips.
 *
 * MIRRORED from upstream `mergeRestagedQuotes`. **The dedupe is the point, not an optimisation:**
 * a dropped excerpt can be recovered from more than one trigger for the same steer, and without it
 * the user's chips multiply every time one fires. Returns [prev] unchanged when nothing new lands.
 *
 * Capped with the already-staged chips winning — a restored tail that could not ride the next send
 * is dropped explicitly rather than shown as a chip the submission would silently discard.
 */
fun mergeRestagedQuotes(prev: List<String>, quotes: List<String>): List<String> {
    val room = MAX_QUOTE_COUNT - prev.size
    if (room <= 0) return prev
    val fresh = quotes.filterNot { it in prev }.distinct().take(room)
    return if (fresh.isEmpty()) prev else prev + fresh
}
