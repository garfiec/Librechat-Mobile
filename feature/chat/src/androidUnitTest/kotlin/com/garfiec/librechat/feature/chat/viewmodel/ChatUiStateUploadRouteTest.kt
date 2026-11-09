package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.response.UploadRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The state-level half of upload routing: which arguments [ChatUiState] hands the router.
 *
 * `UploadRoutingTest` in `:core:model` pins the table and `ChatViewModelUploadRoutingTest` pins
 * the composition. Neither can see this seam — a state that passes the *wrong* provider gets a
 * perfectly correct answer to a question about a selection the user has already left.
 */
class ChatUiStateUploadRouteTest {

    private val pdf = "application/pdf"
    private val docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    private fun state(
        endpoint: String,
        agentProvider: String? = null,
        configs: Map<String, EndpointConfig> = mapOf(endpoint to EndpointConfig()),
    ) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(endpoint, null),
            selectedAgentProvider = agentProvider,
            endpointConfigs = configs,
        ),
    )

    @Test
    fun `an agent provider left over from a previous selection does not route another endpoint`() {
        // `selectedAgentProvider` is documented as null off the agents endpoint, but the collector
        // enforcing that resumes on a LATER dispatch than the write that moved the endpoint. A pick
        // in that gap would route against an agent the selection has already left.
        val stale = state(endpoint = "openAI", agentProvider = "ollama")

        assertThat(stale.uploadRouteFor(pdf)).isEqualTo(UploadRoute.PROVIDER)
    }

    @Test
    fun `the agent provider still routes on the agents endpoint`() {
        val onAgents = state(endpoint = EndpointConstants.AGENTS, agentProvider = "ollama")

        // The guard must narrow *where* the provider is read, not stop reading it — dropping it
        // here would take every agent back to the pre-feature silent drop.
        assertThat(onAgents.uploadRouteFor(docx)).isEqualTo(UploadRoute.TEXT)
    }

    @Test
    fun `an unresolved provider is not a provider-only file`() {
        val unresolved = state(endpoint = EndpointConstants.AGENTS, agentProvider = null)

        // Auto keeps today's behaviour...
        assertThat(unresolved.uploadRouteFor(docx)).isEqualTo(UploadRoute.PROVIDER)
        // ...but "we could not identify the provider" is not "the provider takes it natively and
        // nothing else", and Manual mode must still offer the choice rather than skip the sheet.
        assertThat(unresolved.uploadRouteIsAmbiguous(docx)).isTrue()
    }

    @Test
    fun `a type the server cannot extract is never ambiguous, resolved or not`() {
        val unresolved = state(endpoint = EndpointConstants.AGENTS, agentProvider = null)

        // There is no second option to offer: a zip routed to text is a raw UTF-8 decode.
        assertThat(unresolved.uploadRouteIsAmbiguous("application/zip")).isFalse()
        assertThat(unresolved.uploadRouteIsAmbiguous("image/png")).isFalse()
    }

    @Test
    fun `a retry keeps a route the live selection still offers`() {
        val unresolved = state(endpoint = EndpointConstants.AGENTS, agentProvider = null)
        // Both routes are open for a docx here, so the user's pick stands either way.
        assertThat(unresolved.retryUploadRoute(UploadRoute.TEXT, docx)).isEqualTo(UploadRoute.TEXT)
        assertThat(unresolved.retryUploadRoute(UploadRoute.PROVIDER, docx)).isEqualTo(UploadRoute.PROVIDER)
        // A code upload is the user's destination, never a routing guess.
        val ollama = state(endpoint = EndpointConstants.AGENTS, agentProvider = "ollama")
        assertThat(ollama.retryUploadRoute(UploadRoute.CODE, pdf)).isEqualTo(UploadRoute.CODE)
        // Nothing stored: routed against the selection.
        assertThat(ollama.retryUploadRoute(null, docx)).isEqualTo(UploadRoute.TEXT)
    }

    @Test
    fun `a retry re-routes a route the selection moved away from`() {
        // Native under a document provider, now an agent whose provider can't read it.
        val ollama = state(endpoint = EndpointConstants.AGENTS, agentProvider = "ollama")
        assertThat(ollama.retryUploadRoute(UploadRoute.PROVIDER, pdf)).isEqualTo(UploadRoute.TEXT)

        // Text, now on an agents endpoint that refuses extraction.
        val noContext = state(
            endpoint = EndpointConstants.AGENTS,
            agentProvider = "ollama",
            configs = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf("tools"))),
        )
        assertThat(noContext.retryUploadRoute(UploadRoute.TEXT, pdf)).isEqualTo(UploadRoute.PROVIDER)
    }

    @Test
    fun `file search is offered for documents only, and never chosen for them`() {
        val openAi = state(endpoint = "openAI")

        assertThat(openAi.fileSearchUploadOffered).isTrue()
        assertThat(openAi.uploadRoutesFor(docx)).containsExactly(UploadRoute.TEXT, UploadRoute.FILE_SEARCH).inOrder()
        assertThat(openAi.uploadRoutesFor("image/png")).containsExactly(UploadRoute.PROVIDER)
        assertThat(openAi.uploadRouteFor(docx)).isNotEqualTo(UploadRoute.FILE_SEARCH)
    }

    @Test
    fun `file search follows the role, the capability and a saved agent's tools`() {
        val noRole = state(endpoint = "openAI").let { it.copy(gates = it.gates.copy(fileSearchEnabled = false)) }
        assertThat(noRole.fileSearchUploadOffered).isFalse()

        val noCapability = state(
            endpoint = "openAI",
            configs = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf("tools", "context"))),
        )
        assertThat(noCapability.fileSearchUploadOffered).isFalse()

        fun agent(tools: List<String>) = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1"),
                agents = listOf(Agent(id = "agent_1", tools = tools)),
            ),
        )
        assertThat(agent(listOf(ToolConstants.FILE_SEARCH)).fileSearchUploadOffered).isTrue()
        assertThat(agent(listOf(ToolConstants.EXECUTE_CODE)).fileSearchUploadOffered).isFalse()
    }

    @Test
    fun `a retry keeps file search only while the selection offers it`() {
        val openAi = state(endpoint = "openAI")
        assertThat(openAi.retryUploadRoute(UploadRoute.FILE_SEARCH, docx)).isEqualTo(UploadRoute.FILE_SEARCH)

        val noRole = openAi.copy(gates = openAi.gates.copy(fileSearchEnabled = false))
        assertThat(noRole.retryUploadRoute(UploadRoute.FILE_SEARCH, docx)).isEqualTo(UploadRoute.TEXT)
    }
}
