package com.garfiec.librechat.feature.conversations.components

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import org.junit.Test

class LazyReorderStateTest {

    /** Cards of [size] px stacked from item offset 0, under [topPadding] px of content padding. */
    private class FakeLayout(ids: List<String>, size: Int, topPadding: Int) : ReorderLayout {
        override val visibleItems = ids.mapIndexed { i, id -> ReorderSlot(id, i, i * size, size) }
        override val viewportStartOffset = -topPadding
        override val viewportEndOffset = 2000
        override val firstVisibleItemIndex = 0
        override val firstVisibleItemScrollOffset = 0
        override suspend fun scrollBy(px: Float) = Unit
        override fun requestScrollToItem(index: Int, scrollOffset: Int) = Unit
    }

    private val ids = listOf("A", "B", "C", "D")

    private fun state(topPadding: Int) =
        LazyReorderState(FakeLayout(ids, size = CARD, topPadding = topPadding), TestScope(), autoScrollEdgePx = 48f)

    @Test
    fun `a hold on a card drawn under top padding picks up that card`() {
        val state = state(topPadding = PADDING)

        // The first card is drawn from PADDING to PADDING + CARD in the layout's coordinates.
        assertThat(state.start(Offset(10f, PADDING + CARD / 2f), ids)).isTrue()
        assertThat(state.heldId).isEqualTo("A")
    }

    @Test
    fun `a hold near the bottom of the last card still picks it up`() {
        val state = state(topPadding = PADDING)

        assertThat(state.start(Offset(10f, PADDING + CARD * 4 - 2f), ids)).isTrue()
        assertThat(state.heldId).isEqualTo("D")
    }

    @Test
    fun `a hold on the padding above the first card picks up nothing`() {
        val state = state(topPadding = PADDING)

        assertThat(state.start(Offset(10f, PADDING / 2f), ids)).isFalse()
        assertThat(state.heldId).isNull()
    }

    @Test
    fun `without padding the press maps straight onto the items`() {
        val state = state(topPadding = 0)

        assertThat(state.start(Offset(10f, CARD + 1f), ids)).isTrue()
        assertThat(state.heldId).isEqualTo("B")
    }

    @Test
    fun `a hold that began on an item's menu button picks up nothing until that press ends`() {
        val state = state(topPadding = 0)

        state.onExcludedPress()
        assertThat(state.start(Offset(10f, CARD / 2f), ids)).isFalse()
        assertThat(state.heldId).isNull()
        assertThat(state.isDragging).isFalse()

        state.onExcludedRelease()
        assertThat(state.start(Offset(10f, CARD / 2f), ids)).isTrue()
        assertThat(state.heldId).isEqualTo("A")
    }

    @Test
    fun `an extra release doesn't leave the exclusion counting below zero`() {
        val state = state(topPadding = 0)

        state.onExcludedRelease()
        state.onExcludedPress()
        state.onExcludedRelease()

        assertThat(state.start(Offset(10f, CARD / 2f), ids)).isTrue()
    }

    private companion object {
        const val CARD = 200
        const val PADDING = 300
    }
}
