package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.util.restore
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.SendBlockReason
import com.garfiec.librechat.feature.chat.viewmodel.SendReadinessHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Test

/** The pre-flight refuses a send an enforced server would reject with "No model spec selected". */
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
}
