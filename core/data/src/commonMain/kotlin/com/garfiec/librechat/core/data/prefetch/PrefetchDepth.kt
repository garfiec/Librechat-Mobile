package com.garfiec.librechat.core.data.prefetch

/** How far back the prefetcher keeps conversations warm, in conversations. */
object PrefetchDepth {

    const val MIN = 10

    const val MAX = 200

    /** Fits inside the [PrefetchPolicy.MIN_LIST_PAGES] list refresh, so it costs no extra list requests. */
    const val DEFAULT = 50

    const val STEP = 10

    val RANGE = MIN..MAX

    /** Material3's `Slider.steps` counts only the positions between the ends — hence the -1. */
    const val STEPS_BETWEEN_ENDS: Int = (MAX - MIN) / STEP - 1

    fun snap(depth: Int): Int {
        val snapped = ((depth - MIN + STEP / 2) / STEP) * STEP + MIN
        return snapped.coerceIn(RANGE)
    }
}
