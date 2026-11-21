package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpSignInToolCallCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val url = "https://auth.example.com/authorize?redirect_uri=x"
    private val serverSignIns = mutableListOf<Pair<String, String>>()
    private val actionSignIns = mutableListOf<Pair<String, String>>()

    private fun card(toolCall: ActiveToolCall, preparing: String? = null) {
        val signIn = McpSignIn(
            preparingServer = preparing,
            signIn = { server, link -> serverSignIns += server to link; true },
            signInAction = { action, link -> actionSignIns += action to link; true },
        )
        composeRule.setContent {
            LibreChatTheme {
                CompositionLocalProvider(LocalMcpSignIn provides signIn) {
                    StreamingToolCallCard(toolCall)
                }
            }
        }
    }

    private val actionCall = ActiveToolCall(
        id = "call_1",
        name = "getUser_action_api---example---com",
        authUrl = url,
        authServerName = null,
        authActionId = "act_abc",
    )

    @Test
    fun anAgentActionPromptOffersSignInThroughTheActionEntry() {
        card(actionCall)

        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.waitForIdle()

        assertThat(actionSignIns).containsExactly("act_abc" to url)
        assertThat(serverSignIns).isEmpty()
    }

    @Test
    fun anActionWhoseSignInIsBindingDisablesTheButton() {
        card(actionCall, preparing = "act_abc")

        composeRule.onNodeWithText("Sign in").assertIsNotEnabled()
    }

    @Test
    fun anMcpPromptStillSignsInToItsServer() {
        card(ActiveToolCall(id = "call_2", name = "oauth_mcp_github", authUrl = url, authServerName = "github"))

        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.waitForIdle()

        assertThat(serverSignIns).containsExactly("github" to url)
        assertThat(actionSignIns).isEmpty()
    }
}
