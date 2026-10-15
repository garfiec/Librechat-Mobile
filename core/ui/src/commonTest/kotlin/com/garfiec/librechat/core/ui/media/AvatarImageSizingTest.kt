package com.garfiec.librechat.core.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarImageSizingTest {

    @Test
    fun fitWithin_scales_the_longest_edge_down_to_the_cap() {
        assertEquals(768 to 1024, fitWithin(3000, 4000))
        assertEquals(1024 to 768, fitWithin(4000, 3000))
    }

    @Test
    fun fitWithin_never_scales_up() {
        assertEquals(400 to 300, fitWithin(400, 300))
        assertEquals(1024 to 1024, fitWithin(1024, 1024))
    }

    @Test
    fun fitWithin_keeps_a_thin_panorama_at_least_one_pixel_tall() {
        assertEquals(1024 to 1, fitWithin(20000, 5))
    }
}
