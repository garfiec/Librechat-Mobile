package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.garfiec.librechat.core.data.repository.McpAppResult
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h480dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class McpAppPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longBody = (1..40).joinToString("\n") { "  \"line$it\": \"value $it\"," }

    @Test
    fun toolApprovalScrollsToItsArgumentsAndOptOutAtLargeFont() {
        val action = McpAppAction.ToolCall(1, "trip-budget", "adjust_budget", "{ \"ARGS-TOP\": 1,\n$longBody\n}")
        composeRule.setContent {
            LibreChatTheme { McpAppActionDialog(action, onApprove = {}, onCancel = {}, onStop = {}) }
        }

        val optOut = composeRule.onNodeWithText("Don't ask again for adjust_budget", substring = true)
        optOut.assertIsNotDisplayed()
        optOut.performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("ARGS-TOP", substring = true).assertIsNotDisplayed()
        composeRule.onNode(hasScrollAction() and hasAnyChild(hasText("ARGS-TOP", substring = true))).performScrollTo()
        composeRule.onNodeWithText("ARGS-TOP", substring = true).assertIsDisplayed()
    }

    @Test
    fun legacyMessageScrollsToItsTextAtLargeFont() {
        composeRule.setContent {
            LibreChatTheme { LegacyUiMessageDialog(text = "MSG-TOP\n$longBody", onAnswer = {}) }
        }

        composeRule.onNodeWithText("MSG-TOP", substring = true).assertIsNotDisplayed()
        composeRule.onNode(hasScrollAction() and hasAnyChild(hasText("MSG-TOP", substring = true))).performScrollTo()
        composeRule.onNodeWithText("MSG-TOP", substring = true).assertIsDisplayed()
    }

    @Test
    fun noticeRaisedWhileThePanelIsUpShowsInItAndItsActionWorks() {
        val scope = CoroutineScope(Dispatchers.Main)
        val resource = UiResource.from(
            Json.parseToJsonElement(
                """{"resourceId":"r1","uri":"ui://trip-budget/app","mimeType":"text/html;profile=mcp-app",
                "text":"<p>app</p>","serverName":"trip-budget","toolName":"show_trip_budget","serverBinding":"v1.x"}""",
            ),
        )!!
        // Never started, so it stays Resolving and the frame draws its loading overlay, no web view.
        val host = McpAppHost(
            resource = resource,
            policy = McpAppsPolicy(enabled = true, legacyHtmlEnabled = true),
            repository = UnusedRepository,
            scope = scope,
            callbacks = NoCallbacks,
            hostInfoVersion = "1.0",
            hostContext = JsonObject(emptyMap()),
        )
        val occurrence = McpAppOccurrence(Attachment(), resource, key = "k", ordinal = 1)
        val session = McpAppSession(1, occurrence, host, attempt = 0)
        val snackbarHostState = SnackbarHostState()
        composeRule.setContent {
            LibreChatTheme {
                McpAppPanel(
                    current = session,
                    sessions = listOf(session),
                    awaiting = emptySet(),
                    pool = NoPool,
                    onSelect = {},
                    onMinimize = {},
                    onClose = {},
                    onRetry = {},
                    onHostContext = { _, _ -> },
                    snackbarHostState = snackbarHostState,
                    dialogs = {},
                )
            }
        }

        var result: SnackbarResult? = null
        scope.launch { result = snackbarHostState.showSnackbar("The app's server is busy. Try again.", actionLabel = "Show") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("The app's server is busy", substring = true).assertExists()
        composeRule.onNodeWithText("Show").performClick()
        composeRule.waitForIdle()
        assertThat(result).isEqualTo(SnackbarResult.ActionPerformed)
        scope.cancel()
    }

    private object UnusedRepository : McpAppsRepository {
        override suspend fun validate(serverName: String, serverBinding: String): McpAppResult<Unit> = error("unused")
        override suspend fun readResource(serverName: String, serverBinding: String, uri: String): McpAppResult<JsonObject> =
            error("unused")
        override suspend fun listResources(serverName: String, serverBinding: String, cursor: String?): McpAppResult<JsonObject> =
            error("unused")
        override suspend fun listResourceTemplates(serverName: String, serverBinding: String, cursor: String?): McpAppResult<JsonObject> =
            error("unused")
        override suspend fun callTool(
            serverName: String,
            serverBinding: String,
            toolName: String,
            arguments: JsonObject,
        ): McpAppResult<JsonObject> = error("unused")
    }

    private object NoCallbacks : McpAppHostCallbacks {
        override fun sendMessage(text: String) = false
        override fun openLink(url: String) = Unit
        override fun notice(notice: McpAppNotice, serverName: String) = Unit
        override fun close() = Unit
    }

    private object NoPool : McpAppViewPool {
        override fun retain(sessionIds: Set<Long>, evicted: Set<String>) = Unit
        override fun snapshot(occurrenceKey: String): ImageBitmap? = null
    }
}
