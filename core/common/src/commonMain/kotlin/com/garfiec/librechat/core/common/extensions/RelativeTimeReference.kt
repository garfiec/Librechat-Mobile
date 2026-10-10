package com.garfiec.librechat.core.common.extensions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * "Now", as the relative formatters (`messageLabel`, `listLabel`, `toDateGroup`) see it.
 *
 * Resolving the clock and the system timezone inside each formatter would run that loop-invariant
 * work once per row of a list mapping. Taking it as a value lets a caller resolve it once — and,
 * more importantly, lets a *ticking* value (`LocalRelativeTimeReference` in core/ui) drive
 * recomposition, which a clock read buried in a formatter never could.
 */
data class RelativeTimeReference(
    val now: Instant,
    val timeZone: TimeZone,
) {
    /**
     * Eager, and cheap to keep that way: references are hoisted, so this is computed roughly once
     * per list emission and once per clock tick — never per row. (An earlier revision made this
     * `by lazy` back when every row built its own reference from the default argument; that is no
     * longer how any production call site works, and `by lazy` would now add a volatile read to
     * `toDateGroup`'s per-row path to save a handful of conversions a minute.)
     */
    val today: LocalDate = now.toLocalDateTime(timeZone).date

    companion object {
        fun current(): RelativeTimeReference =
            RelativeTimeReference(Clock.System.now(), TimeZone.currentSystemDefault())
    }
}
