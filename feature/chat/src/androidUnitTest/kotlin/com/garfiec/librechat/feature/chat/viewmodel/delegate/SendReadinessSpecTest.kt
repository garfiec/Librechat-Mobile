package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.chat.util.restore
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.SendBlockReason
import com.garfiec.librechat.feature.chat.viewmodel.SendReadinessHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.time.Instant

/**
 * The pre-flight refuses a send the server would reject outright: one naming no spec under
 * `enforce` ("No model spec selected"), and one to a user-key endpoint with no key (`no_user_key`).
 */
class SendReadinessSpecTest {

    private val spec = ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))

    private fun refusal(selection: ModelSelectionState): SendBlockReason? {
        val handle = ChatStateHandle(MutableStateFlow(ChatUiState(selection = selection)), TestScope())
        var reason: SendBlockReason? = null
        SendReadinessDelegate(SendReadinessHandle(handle)) { reason = it }.runWhenSendReady {}
        return reason
    }

    @Test
    fun enforcedServerRefusesARawPickOnAnAddedEndpoint() {
        val reason = refusal(
            ModelSelectionState(
                choice = ModelChoice.Raw("agents", "agent_1"),
                modelSpecs = ModelSpecs(list = listOf(spec), enforce = true, addedEndpoints = listOf("agents")),
            ),
        )
        assertThat(reason).isEqualTo(SendBlockReason.SelectModel)
    }

    @Test
    fun enforcedServerAcceptsASpecSelection() {
        val reason = refusal(
            ModelSelectionState(
                choice = ModelChoice.restore("openAI", "gpt-4.1", "fast"),
                modelSpecs = ModelSpecs(list = listOf(spec), enforce = true),
            ),
        )
        assertThat(reason).isNotEqualTo(SendBlockReason.SelectModel)
    }

    private fun comparisonRefusal(lane: ComparisonState, enforce: Boolean = true): SendBlockReason? {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.restore("openAI", "gpt-4.1", "fast"),
                modelSpecs = ModelSpecs(list = listOf(spec), enforce = enforce),
            ),
            comparisonState = lane,
        )
        val handle = ChatStateHandle(MutableStateFlow(state), TestScope())
        var reason: SendBlockReason? = null
        SendReadinessDelegate(SendReadinessHandle(handle)) { reason = it }.runWhenSendReady {}
        return reason
    }

    private fun laneB(spec: String? = null, reopenedId: String? = null, endpoint: String = "openAI", model: String = "gpt-4o") =
        ComparisonState(
            isEnabled = true,
            secondaryEndpoint = endpoint,
            secondaryModel = model,
            secondarySpec = spec,
            reopenedSecondaryId = reopenedId,
        )

    @Test
    fun enforcedServerRefusesALaneBWithNoSpec() {
        // A reopened lane whose id names no spec on this server.
        val reason = comparisonRefusal(laneB(reopenedId = "openAI__gpt-4o___GPT-4o____1"))
        assertThat(reason).isEqualTo(SendBlockReason.SelectSecondaryModel)
    }

    @Test
    fun laneBWithASpecASavedAgentOrNoEnforcePasses() {
        assertThat(comparisonRefusal(laneB(spec = "fast", model = "gpt-4.1"))).isNotEqualTo(SendBlockReason.SelectSecondaryModel)
        // A reopened lane on a pair with one spec takes that spec.
        val reopened = laneB(reopenedId = "openAI__gpt-4.1____1", model = "gpt-4.1")
        assertThat(comparisonRefusal(reopened)).isNotEqualTo(SendBlockReason.SelectSecondaryModel)
        assertThat(comparisonRefusal(laneB(endpoint = "agents", model = "agent_1"))).isNotEqualTo(SendBlockReason.SelectSecondaryModel)
        assertThat(comparisonRefusal(laneB(), enforce = false)).isNull()
    }

    private fun keyRefusal(keyState: KeyState?): SendBlockReason? = refusal(
        ModelSelectionState(
            choice = ModelChoice.Raw("byok", "model-a"),
            endpointKeyStates = keyState?.let { mapOf("byok" to it) }.orEmpty(),
        ),
    )

    @Test
    fun aUserKeyEndpointWithNoKeyIsRefused() {
        assertThat(keyRefusal(KeyState.Unset)).isEqualTo(SendBlockReason.ApiKeyRequired("byok"))
        val expired = KeyState.Expired(expiresAt = Instant.parse("2026-01-01T00:00:00Z"), wire = "2026-01-01T00:00:00Z")
        assertThat(keyRefusal(expired)).isEqualTo(SendBlockReason.ApiKeyRequired("byok"))
    }

    @Test
    fun aKeyThatIsSetLoadingOrNotNeededPasses() {
        assertThat(keyRefusal(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))).isNull()
        assertThat(keyRefusal(KeyState.Loading)).isNull()
        assertThat(keyRefusal(null)).isNull()
    }
}
