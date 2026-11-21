package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the host's premise: whatever shape the artifact takes, its viewer is composed once, so the
 * web view in it (and the page it holds) is never rebuilt by a rotation, fold or expand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w900dp-h800dp")
class ArtifactPresentationHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val entry = ArtifactViewerHandoff.Entry(
        artifact = Artifact(identifier = "a", type = "text/html", title = "A", language = null, content = "<p>a</p>"),
        versions = emptyList(),
    )

    @Test
    fun viewer_isComposedOnce_acrossShapes() {
        val state = ArtifactPresentationState()
        var entered = 0
        var left = 0

        composeRule.setContent {
            LibreChatTheme {
                ArtifactPresentationHost(
                    state = state,
                    automatic = true,
                    splitFraction = 0.5f,
                    onSplitFractionChange = {},
                    origin = ArtifactOrigin(),
                    panelScope = { panel -> panel() },
                    viewer = { _, _, modifier ->
                        DisposableEffect(Unit) {
                            entered++
                            onDispose { left++ }
                        }
                        Box(modifier) { Text("viewer") }
                    },
                ) { Text("chat") }
            }
        }

        composeRule.runOnIdle { state.show(entry, opensFull = true) }
        composeRule.waitForIdle()
        // Beside the chat in a 900dp window, then the whole window, then back beside it.
        composeRule.runOnIdle { state.expanded = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.expanded = false }
        composeRule.waitForIdle()

        assertThat(entered).isEqualTo(1)
        assertThat(left).isEqualTo(0)
    }

    @Test
    fun viewer_isComposedOnce_fromSheetToWholeWindow() {
        val state = ArtifactPresentationState()
        var entered = 0

        composeRule.setContent {
            LibreChatTheme {
                ArtifactPresentationHost(
                    state = state,
                    automatic = false,
                    splitFraction = 0.5f,
                    onSplitFractionChange = {},
                    origin = ArtifactOrigin(),
                    panelScope = { panel -> panel() },
                    viewer = { _, _, modifier ->
                        DisposableEffect(Unit) {
                            entered++
                            onDispose {}
                        }
                        Box(modifier) { Text("viewer") }
                    },
                ) { Text("chat") }
            }
        }

        composeRule.runOnIdle { state.show(entry, opensFull = false) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.expanded = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.expanded = false }
        composeRule.waitForIdle()

        assertThat(entered).isEqualTo(1)
    }
}
