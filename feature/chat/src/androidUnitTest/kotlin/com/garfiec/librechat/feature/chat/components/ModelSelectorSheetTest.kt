package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelSelectorSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusingSearchExpandsTheHalfHeightSheet() {
        composeRule.setContent {
            LibreChatTheme {
                ModelSelectorSheet(
                    endpointConfigs = emptyMap(),
                    availableModels = mapOf(
                        "openAI" to listOf("gpt-6-terra", "gpt-5.2"),
                        "anthropic" to listOf("claude-opus-5-5"),
                    ),
                    agents = emptyList(),
                    selectedEndpoint = "openAI",
                    selectedModel = "gpt-6-terra",
                    onModelSelect = { _, _ -> },
                    modelSelect = true,
                    onDismiss = {},
                    onSetApiKey = {},
                )
            }
        }
        composeRule.waitForIdle()
        val search = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        val before = search.getUnclippedBoundsInRoot().top

        search.performClick()
        composeRule.waitForIdle()
        val after = search.getUnclippedBoundsInRoot().top

        assertWithMessage("focusing search should expand the sheet: search field top stayed at $after (was $before)")
            .that(after.value)
            .isLessThan(before.value / 2)
    }
}
