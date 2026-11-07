package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The steer and queue region hands what it can't scroll to the thread: the rest of a drag at its
 * edge, the momentum of a release that happened on the thread, and the leftover of its own fling.
 */
class ThreadHandOffConnectionTest {
    private class RecordingDrags(override val canTakeDrag: Boolean = true) : ThreadDrags {
        val calls = mutableListOf<String>()
        override fun drag(delta: Float) {
            calls += "drag $delta"
        }

        override fun fling(velocity: Float) {
            calls += "fling $velocity"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private val drags = RecordingDrags()
    private val connection = ThreadHandOffConnection(drags)

    @Test
    fun aDragPastTheRegionsEdgeScrollsTheThreadAndIsConsumed() {
        val consumed = connection.onPostScroll(Offset(0f, 2f), Offset(0f, 7f), NestedScrollSource.UserInput)
        assertThat(consumed).isEqualTo(Offset(0f, 7f))
        assertThat(drags.calls).containsExactly("drag 7.0")
    }

    @Test
    fun theRegionsOwnFlingDeltasAreNotDrags() {
        val consumed = connection.onPostScroll(Offset.Zero, Offset(0f, 7f), NestedScrollSource.SideEffect)
        assertThat(consumed).isEqualTo(Offset.Zero)
        assertThat(drags.calls).isEmpty()
    }

    @Test
    fun aDragTheRegionTakesBackLeavesTheThreadWhereItIs() {
        connection.onPostScroll(Offset.Zero, Offset(0f, 7f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, -3f), Offset.Zero, NestedScrollSource.UserInput)
        assertThat(drags.calls).containsExactly("drag 7.0", "cancel").inOrder()
    }

    @Test
    fun aReleaseWhileHandingOffFlingsTheThreadNotTheRegion() = runTest {
        connection.onPostScroll(Offset.Zero, Offset(0f, 7f), NestedScrollSource.UserInput)
        val taken = connection.onPreFling(Velocity(0f, 900f))
        assertThat(taken).isEqualTo(Velocity(0f, 900f))
        assertThat(drags.calls).containsExactly("drag 7.0", "fling 900.0").inOrder()
    }

    @Test
    fun aReleaseInsideTheRegionLeavesTheFlingToIt() = runTest {
        assertThat(connection.onPreFling(Velocity(0f, 900f))).isEqualTo(Velocity.Zero)
        assertThat(drags.calls).isEmpty()
    }

    @Test
    fun theLeftoverOfTheRegionsFlingCarriesOnInTheThread() = runTest {
        val taken = connection.onPostFling(Velocity(0f, 600f), Velocity(0f, 300f))
        assertThat(taken).isEqualTo(Velocity(0f, 300f))
        assertThat(drags.calls).containsExactly("fling 300.0")
    }

    @Test
    fun aFlingTheRegionUsedUpReachesNothing() = runTest {
        assertThat(connection.onPostFling(Velocity(0f, 900f), Velocity.Zero)).isEqualTo(Velocity.Zero)
        assertThat(drags.calls).isEmpty()
    }

    @Test
    fun withNoThreadTheRegionKeepsEverything() = runTest {
        val none = ThreadHandOffConnection(RecordingDrags(canTakeDrag = false))
        assertThat(none.onPostScroll(Offset.Zero, Offset(0f, 7f), NestedScrollSource.UserInput)).isEqualTo(Offset.Zero)
        assertThat(none.onPostFling(Velocity.Zero, Velocity(0f, 300f))).isEqualTo(Velocity.Zero)
    }
}
