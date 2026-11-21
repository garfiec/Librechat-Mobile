package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A drag handed to the thread is one user scroll from its first delta to its fling, as a finger on
 * the list makes: that is what the tail follower reads as the user scrolling away mid-reply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadScrollHandleTest {
    private val scrolled = mutableListOf<Float>()
    private val list = ScrollableState { delta ->
        scrolled += delta
        delta
    }
    private val flings = mutableListOf<Float>()
    private val fling = object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            flings += initialVelocity
            return 0f
        }
    }

    @Test
    fun aDragIsOneUserScrollUntilItsFling() = runTest {
        val handle = ThreadScrollHandle(backgroundScope)
        handle.bind(list, fling)

        handle.drag(12f)
        runCurrent()
        assertThat(list.isScrollInProgress).isTrue()
        handle.drag(-4f)
        runCurrent()
        assertThat(list.isScrollInProgress).isTrue()

        handle.fling(800f)
        runCurrent()
        assertThat(list.isScrollInProgress).isFalse()
        // A finger moving down scrolls the top-to-bottom list back.
        assertThat(scrolled).containsExactly(-12f, 4f).inOrder()
        assertThat(flings).containsExactly(-800f)
    }

    @Test
    fun cancelEndsTheScrollWithoutAFling() = runTest {
        val handle = ThreadScrollHandle(backgroundScope)
        handle.bind(list, fling)
        handle.drag(12f)
        runCurrent()
        handle.cancel()
        runCurrent()
        assertThat(list.isScrollInProgress).isFalse()
        assertThat(flings).isEmpty()
    }

    @Test
    fun aFingerOnTheListTakesTheScrollAndTheNextDragStartsAfresh() = runTest {
        val handle = ThreadScrollHandle(backgroundScope)
        handle.bind(list, fling)
        handle.drag(12f)
        runCurrent()
        // The list's own drag runs at the same priority and takes the scroll.
        val finger = launch { list.scroll(MutatePriority.UserInput) { scrollBy(1f) } }
        runCurrent()
        finger.join()
        handle.drag(5f)
        runCurrent()
        assertThat(scrolled).containsExactly(-12f, 1f, -5f).inOrder()
        assertThat(list.isScrollInProgress).isTrue()
    }

    @Test
    fun nothingBoundTakesNothing() = runTest {
        val handle = ThreadScrollHandle(backgroundScope)
        assertThat(handle.canTakeDrag).isFalse()
        handle.drag(12f)
        handle.fling(800f)
        runCurrent()
        assertThat(scrolled).isEmpty()
        assertThat(flings).isEmpty()
    }

    @Test
    fun unbindingTheListOnScreenEndsItsDrag() = runTest {
        val handle = ThreadScrollHandle(backgroundScope)
        handle.bind(list, fling)
        handle.drag(12f)
        runCurrent()
        handle.unbind(list)
        runCurrent()
        assertThat(handle.canTakeDrag).isFalse()
        assertThat(list.isScrollInProgress).isFalse()
    }
}
