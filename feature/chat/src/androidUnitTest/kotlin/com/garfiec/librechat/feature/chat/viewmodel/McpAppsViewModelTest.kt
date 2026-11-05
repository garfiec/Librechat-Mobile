package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsent
import com.garfiec.librechat.core.data.datastore.McpAppLocationConsentStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpAppFailure
import com.garfiec.librechat.core.data.repository.McpAppResult
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.core.model.mcp.selectToolCallUiResources
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpAppsViewModelTest {

    private val config = MutableStateFlow<StartupConfig?>(null)
    private val refreshes = MutableSharedFlow<StartupConfig>(extraBufferCapacity = 1)
    private val repository = mockk<McpAppsRepository>()
    private val consents = mutableMapOf<String, McpAppLocationConsent>()
    private val consentStore = mockk<McpAppLocationConsentStore>().also { store ->
        coEvery { store.consent(any()) } answers { consents[firstArg()] ?: McpAppLocationConsent.NOT_ASKED }
        coEvery { store.set(any(), any()) } answers { consents[firstArg()] = secondArg() }
    }

    private val occurrence = selectToolCallUiResources(
        listOf(
            Attachment(
                type = "ui_resources",
                toolCallId = "call_1",
                uiResources = Json.parseToJsonElement(
                    """[{"resourceId":"r1","uri":"ui://w","mimeType":"text/html;profile=mcp-app","text":"<p>x</p>",
                    "serverName":"weather","toolName":"show_forecast","serverBinding":"v1.abc"}]""",
                ),
            ),
        ),
    ).single()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.validate(any(), any()) } returns McpAppResult.Success(Unit)
        coEvery { repository.callTool(any(), any(), any(), any()) } returns McpAppResult.Success(JsonObject(emptyMap()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): McpAppsViewModel {
        val configRepository = mockk<ConfigRepository>(relaxed = true)
        every { configRepository.startupConfig } returns config
        every { configRepository.startupConfigRefreshes } returns refreshes
        val appInfo = mockk<AppInfo>()
        every { appInfo.versionName } returns "1.0"
        return McpAppsViewModel(
            repository = repository,
            appInfo = appInfo,
            configRepository = configRepository,
            locationConsent = consentStore,
        )
    }

    private fun enabledConfig() = StartupConfig(
        mcpApps = Json.parseToJsonElement("""{"enabled":true,"legacyHtmlEnabled":true}"""),
    )

    @Test
    fun `nothing opens while the server's policy is off`() {
        val vm = viewModel()
        vm.open(occurrence)
        assertThat(vm.state.value.sessions).isEmpty()
    }

    @Test
    fun `opening starts the app straight away, and close tears it down`() {
        config.value = enabledConfig()
        val vm = viewModel()

        vm.open(occurrence)
        val session = vm.state.value.sessions.single()
        assertThat(session.occurrence).isEqualTo(occurrence)
        // Inline in its card: the panel stays down.
        assertThat(vm.state.value.panelId).isNull()

        vm.close(session.id)
        assertThat(vm.state.value.sessions).isEmpty()
    }

    @Test
    fun `retry starts a fresh attempt`() {
        config.value = enabledConfig()
        val vm = viewModel()
        vm.open(occurrence)
        val first = vm.state.value.sessions.single()
        vm.expand(first.id)
        vm.retry(first.id)
        val next = vm.state.value.sessions.single()
        assertThat(next.attempt).isEqualTo(first.attempt + 1)
        assertThat(next.host).isNotSameInstanceAs(first.host)
        assertThat(next.id).isNotEqualTo(first.id)
        // Still the one in the panel.
        assertThat(vm.state.value.panelId).isEqualTo(next.id)
    }

    @Test
    fun `apps reported off stay off until the server answers a config again, even an unchanged one`() {
        config.value = enabledConfig()
        coEvery { repository.validate(any(), any()) } returns McpAppResult.Failure(McpAppFailure.APPS_DISABLED)
        val vm = viewModel()

        vm.open(occurrence)
        assertThat(vm.policy.value).isEqualTo(McpAppsPolicy.DISABLED)

        // An equal config is deduped by the StateFlow: on its own it lifts nothing.
        config.value = enabledConfig()
        assertThat(vm.policy.value).isEqualTo(McpAppsPolicy.DISABLED)

        refreshes.tryEmit(enabledConfig())
        assertThat(vm.policy.value.enabled).isTrue()
    }

    // --- location: this app's question per server, then the OS ---

    private val locationApp = selectToolCallUiResources(
        listOf(
            Attachment(
                type = "ui_resources",
                toolCallId = "call_2",
                uiResources = Json.parseToJsonElement(
                    """[{"resourceId":"r2","uri":"ui://m","mimeType":"text/html;profile=mcp-app","text":"<p>m</p>",
                    "serverName":"weather","toolName":"map","serverBinding":"v1.abc","permissions":{"geolocation":{}}}]""",
                ),
            ),
        ),
    ).single()

    private fun openedForLocation(): Pair<McpAppsViewModel, MutableList<Boolean>> {
        config.value = enabledConfig()
        val vm = viewModel()
        vm.open(locationApp)
        val answers = mutableListOf<Boolean>()
        val session = vm.state.value.sessions.single()
        vm.expand(session.id)
        session.host.onLocationPrompt { answers += it }
        return vm to answers
    }

    @Test
    fun `a first request asks, Allow is remembered and goes on to the OS permission`() {
        val (vm, answers) = openedForLocation()
        val id = vm.state.value.sessions.single().id
        val asked = vm.locationRequest.value
        assertThat(asked?.sessionId).isEqualTo(id)
        assertThat(asked?.serverName).isEqualTo("weather")
        assertThat(asked?.stage).isEqualTo(McpAppLocationRequest.Stage.CONSENT)
        vm.answerLocationConsent(allow = true)
        assertThat(consents["weather"]).isEqualTo(McpAppLocationConsent.ALLOWED)
        assertThat(vm.locationRequest.value?.stage).isEqualTo(McpAppLocationRequest.Stage.OS_PERMISSION)
        vm.answerLocationPermission(granted = true)
        assertThat(answers).containsExactly(true)
        assertThat(vm.locationRequest.value).isNull()
    }

    @Test
    fun `a repeated request from the same App is a new question, so its dialog arms again`() {
        val (vm, answers) = openedForLocation()
        val first = vm.locationRequest.value
        vm.state.value.sessions.single().host.onLocationPrompt { answers += it }
        val second = vm.locationRequest.value
        assertThat(answers).containsExactly(false)
        assertThat(second?.stage).isEqualTo(McpAppLocationRequest.Stage.CONSENT)
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `a withdrawn prompt is refused and its question dropped, nothing remembered`() {
        val (vm, answers) = openedForLocation()
        val withdrawal = vm.state.value.sessions.single().host.onLocationPrompt { answers += it }
        answers.clear() // the second prompt refused the first
        withdrawal.withdraw()
        assertThat(answers).containsExactly(false)
        assertThat(vm.locationRequest.value).isNull()
        assertThat(consents["weather"]).isNull()
    }

    @Test
    fun `refusing is remembered and answers the App at once`() {
        val (vm, answers) = openedForLocation()
        vm.answerLocationConsent(allow = false)
        assertThat(consents["weather"]).isEqualTo(McpAppLocationConsent.REFUSED)
        assertThat(answers).containsExactly(false)
        assertThat(vm.locationRequest.value).isNull()
    }

    @Test
    fun `an allowed server skips the question, the OS still decides`() {
        consents["weather"] = McpAppLocationConsent.ALLOWED
        val (vm, answers) = openedForLocation()
        assertThat(vm.locationRequest.value?.stage).isEqualTo(McpAppLocationRequest.Stage.OS_PERMISSION)
        vm.answerLocationPermission(granted = false)
        assertThat(answers).containsExactly(false)
    }

    @Test
    fun `a refused server is not offered location, so its frame never asks`() {
        consents["weather"] = McpAppLocationConsent.REFUSED
        val (vm, answers) = openedForLocation()
        assertThat(vm.locationRequest.value).isNull()
        assertThat(answers).containsExactly(false)
    }

    @Test
    fun `closing the App refuses a question still on screen`() {
        val (vm, answers) = openedForLocation()
        vm.close(vm.state.value.sessions.single().id)
        assertThat(answers).containsExactly(false)
        assertThat(vm.locationRequest.value).isNull()
    }

    @Test
    fun `retry refuses a question the old attempt left on screen`() {
        val (vm, answers) = openedForLocation()
        vm.retry(vm.state.value.sessions.single().id)
        assertThat(answers).containsExactly(false)
        assertThat(vm.locationRequest.value).isNull()
    }

    // --- legacy MCP-UI messages: confirmed, then sent through the chat ---

    @Test
    fun `a legacy message waits for the user, then goes through the chat`() {
        val vm = viewModel()
        val sent = mutableListOf<String>()
        vm.attachMessageSink { sent += it; true }
        var answer: Boolean? = null
        vm.requestLegacyMessage("Book the 9am") { answer = it }
        val request = vm.legacyMessage.value!!
        assertThat(sent).isEmpty()
        vm.answerLegacyMessage(request.id, send = true)
        assertThat(sent).containsExactly("Book the 9am")
        assertThat(answer).isTrue()
        assertThat(vm.legacyMessage.value).isNull()
    }

    @Test
    fun `cancel, a refusing chat, a second request and a stale answer all send nothing`() {
        val vm = viewModel()
        vm.attachMessageSink { false }
        val answers = mutableListOf<Boolean>()
        vm.requestLegacyMessage("one") { answers += it }
        vm.requestLegacyMessage("two") { answers += it }
        assertThat(answers).containsExactly(false)
        val first = vm.legacyMessage.value!!
        vm.answerLegacyMessage(first.id + 1, send = true)
        assertThat(vm.legacyMessage.value).isEqualTo(first)
        vm.answerLegacyMessage(first.id, send = true)
        assertThat(answers).containsExactly(false, false).inOrder()

        vm.requestLegacyMessage("three") { answers += it }
        vm.answerLegacyMessage(vm.legacyMessage.value!!.id, send = false)
        assertThat(answers).containsExactly(false, false, false)
    }

    // --- several Apps at once ---

    private fun app(n: Int, extra: String = "") = selectToolCallUiResources(
        listOf(
            Attachment(
                type = "ui_resources",
                toolCallId = "call_multi_$n",
                uiResources = Json.parseToJsonElement(
                    """[{"resourceId":"m$n","uri":"ui://m$n","mimeType":"text/html;profile=mcp-app","text":"<p>$n</p>",
                    "serverName":"weather","toolName":"tool_$n","serverBinding":"v1.abc"$extra}]""",
                ),
            ),
        ),
    ).single()

    private fun enabledVm(maxActiveViews: Int? = null): McpAppsViewModel {
        val limit = maxActiveViews?.let { ""","maxActiveViews":$it""" }.orEmpty()
        config.value = StartupConfig(mcpApps = Json.parseToJsonElement("""{"enabled":true,"legacyHtmlEnabled":true$limit}"""))
        return viewModel()
    }

    private fun McpAppsViewModel.ids() = state.value.sessions.map { it.id }

    private fun McpAppsViewModel.askTool(id: Long, toolName: String = "refresh") {
        val host = state.value.sessions.first { it.id == id }.host
        host.onRelayMessage("""{"jsonrpc":"2.0","method":"ui/notifications/sandbox-proxy-ready","params":{}}""")
        host.onRelayMessage(
            """{"jsonrpc":"2.0","id":1,"method":"ui/initialize","params":{"protocolVersion":"2026-01-26"}}""",
        )
        host.onRelayMessage("""{"jsonrpc":"2.0","method":"ui/notifications/initialized"}""")
        host.onRelayMessage("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"$toolName","arguments":{}}}""")
    }

    @Test
    fun `up to three Apps open at once, the next is refused with the capacity notice`() = runTest {
        val vm = enabledVm(maxActiveViews = 32)
        val events = mutableListOf<McpAppEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        (1..4).forEach { vm.open(app(it)) }
        assertThat(vm.ids()).hasSize(3)
        assertThat(events).contains(McpAppEvent.AtCapacity)
        // Opening one already open opens nothing new.
        vm.open(app(1))
        assertThat(vm.ids()).hasSize(3)
    }

    @Test
    fun `the server's limit and a low-RAM device lower the cap`() {
        val vm = enabledVm(maxActiveViews = 2)
        (1..3).forEach { vm.open(app(it)) }
        assertThat(vm.ids()).hasSize(2)

        val lowRam = enabledVm()
        lowRam.setLowRamDevice(true)
        (1..2).forEach { lowRam.open(app(it)) }
        assertThat(lowRam.ids()).hasSize(1)
    }

    @Test
    fun `back minimizes and keeps the App, close in the panel moves to another tab`() {
        val vm = enabledVm()
        vm.open(app(1))
        vm.open(app(2))
        val (a, b) = vm.ids()
        vm.expand(a)
        vm.expand(b)
        vm.minimize()
        assertThat(vm.state.value.panelId).isNull()
        assertThat(vm.ids()).containsExactly(a, b)

        vm.expand(b)
        vm.close(b)
        assertThat(vm.state.value.panelId).isEqualTo(a)
        vm.close(a)
        assertThat(vm.state.value.panelId).isNull()
    }

    @Test
    fun `an approved message minimizes the panel and the App keeps running`() {
        val vm = enabledVm()
        vm.attachMessageSink { true }
        vm.open(app(1))
        val id = vm.ids().single()
        vm.expand(id)
        val host = vm.state.value.sessions.single().host
        host.onRelayMessage("""{"jsonrpc":"2.0","method":"ui/notifications/sandbox-proxy-ready","params":{}}""")
        host.onRelayMessage("""{"jsonrpc":"2.0","method":"ui/notifications/initialized"}""")
        host.onRelayMessage("""{"jsonrpc":"2.0","id":3,"method":"ui/message","params":{"content":[{"type":"text","text":"hi"}]}}""")
        host.approve(host.pendingAction.value!!)
        assertThat(vm.state.value.panelId).isNull()
        assertThat(vm.ids()).containsExactly(id)
    }

    @Test
    fun `only an App on screen shows its question, one off screen waits with a dot and a notice`() = runTest {
        val vm = enabledVm()
        val events = mutableListOf<McpAppEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.open(app(1))
        vm.open(app(2))
        val (a, b) = vm.ids()
        vm.setInlineVisible(a, true)
        vm.askTool(b)
        assertThat(vm.awaiting.value).containsExactly(b)
        assertThat(vm.presented.value).isNull()
        assertThat(events.filterIsInstance<McpAppEvent.Waiting>().single().sessionId).isEqualTo(b)

        // "Show" expands it; in the panel it is the App on screen.
        vm.expand(b)
        assertThat(vm.presented.value).isEqualTo(b)

        // The panel covers the thread: a visible card's question waits too.
        vm.askTool(a)
        assertThat(vm.presented.value).isEqualTo(b)
        vm.minimize()
        vm.setInlineVisible(b, false)
        assertThat(vm.presented.value).isEqualTo(a)
    }

    @Test
    fun `grants stay with the App that was given them`() {
        val vm = enabledVm()
        vm.open(app(1))
        vm.open(app(2))
        val (a, b) = vm.state.value.sessions
        vm.setInlineVisible(a.id, true)
        vm.askTool(a.id)
        a.host.approve(a.host.pendingAction.value!!, dontAskAgain = true)
        assertThat(a.host.autoRunTools.value).containsExactly("refresh")
        assertThat(b.host.autoRunTools.value).isEmpty()
        vm.askTool(b.id)
        assertThat(b.host.pendingAction.value).isNotNull()
    }

    @Test
    fun `memory pressure evicts Apps the user can't see, never one waiting on a question`() {
        val vm = enabledVm()
        (1..3).forEach { vm.open(app(it)) }
        val (visible, waiting, idle) = vm.state.value.sessions
        vm.setInlineVisible(visible.id, true)
        vm.askTool(waiting.id)

        vm.onMemoryPressure()
        assertThat(vm.ids()).containsExactly(visible.id, waiting.id)
        assertThat(vm.state.value.evicted).containsExactly(idle.occurrence.key)

        // Tap to resume opens it again, fresh.
        vm.open(idle.occurrence)
        assertThat(vm.state.value.evicted).isEmpty()
        assertThat(vm.state.value.sessionFor(idle.occurrence.key)!!.host).isNotSameInstanceAs(idle.host)
    }

    @Test
    fun `a second App's location question is refused while the first one's is open`() {
        val vm = enabledVm()
        vm.open(app(1, ""","permissions":{"geolocation":{}}"""))
        vm.open(app(2, ""","permissions":{"geolocation":{}}"""))
        val (a, b) = vm.state.value.sessions
        val answers = mutableListOf<Pair<Long, Boolean>>()
        a.host.onLocationPrompt { answers += a.id to it }
        b.host.onLocationPrompt { answers += b.id to it }
        assertThat(answers).containsExactly(b.id to false)
        assertThat(vm.locationRequest.value?.sessionId).isEqualTo(a.id)
        // Closing the other App leaves this one's question alone.
        vm.close(b.id)
        assertThat(vm.locationRequest.value?.sessionId).isEqualTo(a.id)
        vm.close(a.id)
        assertThat(answers).containsExactly(b.id to false, a.id to false).inOrder()
    }

    @Test
    fun `an App on screen may go full screen and back, one off screen may not`() {
        val vm = enabledVm()
        vm.open(app(1))
        val session = vm.state.value.sessions.single()
        val host = session.host
        host.onRelayMessage("""{"jsonrpc":"2.0","id":5,"method":"ui/request-display-mode","params":{"mode":"fullscreen"}}""")
        assertThat(vm.state.value.panelId).isNull()

        vm.setInlineVisible(session.id, true)
        host.onRelayMessage("""{"jsonrpc":"2.0","id":6,"method":"ui/request-display-mode","params":{"mode":"fullscreen"}}""")
        assertThat(vm.state.value.panelId).isEqualTo(session.id)
        host.onRelayMessage("""{"jsonrpc":"2.0","id":7,"method":"ui/request-display-mode","params":{"mode":"inline"}}""")
        assertThat(vm.state.value.panelId).isNull()
    }

    @Test
    fun `an inline slot freezes at its first height`() {
        val vm = enabledVm()
        vm.open(app(1))
        val id = vm.ids().single()
        vm.freezeInlineHeight(id, 240)
        vm.freezeInlineHeight(id, 600)
        assertThat(vm.state.value.inlineHeights[id]).isEqualTo(240)
        vm.close(id)
        assertThat(vm.state.value.inlineHeights).isEmpty()
    }

    @Test
    fun `leaving the chat closes every App`() {
        val vm = enabledVm()
        (1..2).forEach { vm.open(app(it)) }
        val hosts = vm.state.value.sessions.map { it.host }
        vm.expand(vm.ids().first())
        vm.closeAll()
        assertThat(vm.state.value.sessions).isEmpty()
        assertThat(vm.state.value.panelId).isNull()
        hosts.forEach { host ->
            host.onRelayMessage("""{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"x","arguments":{}}}""")
            assertThat(host.pendingAction.value).isNull()
        }
    }
}
