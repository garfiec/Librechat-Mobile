package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.feature.chat.components.LandingHeroIcon
import com.garfiec.librechat.feature.chat.viewmodel.ChatPrefsState
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/** [landingText], [landingHeroIcon] and [replySenderName]: what the new chat and the reply in progress show. */
class LandingTextTest {

    private val modelSpec = ModelSpec(
        name = "coder",
        label = "Coder",
        description = "Writes code",
        preset = Preset(endpoint = "openAI", model = "gpt-5", greeting = "Hi, I code"),
    )
    private val agentSpec = ModelSpec(
        name = "helper",
        label = "Helper Spec",
        description = "Spec words",
        preset = Preset(endpoint = EndpointConstants.AGENTS, agentId = "a1"),
        showOnLanding = true,
    )
    private val agent =
        Agent(id = "a1", name = "Travel Planner", description = "Plans trips", avatar = JsonPrimitive("https://av/a.png"))

    private fun state(spec: ModelSpec?, temporary: Boolean = false): ChatUiState {
        val choice = when {
            spec == null -> ModelChoice.Raw("openAI", "gpt-5")
            else -> ModelChoice.Spec(spec.name, spec.preset!!.endpoint!!, spec.preset!!.model ?: spec.preset!!.agentId!!)
        }
        return ChatUiState(
            selection = ModelSelectionState(
                choice = choice,
                modelSpecs = ModelSpecs(list = listOfNotNull(spec)),
                agents = listOf(agent),
            ),
            conversation = ConversationMetaState(isTemporaryChat = temporary),
            prefs = ChatPrefsState(serverUrl = "https://srv"),
        )
    }

    @Test
    fun greetingHeadingKeepsTheGreetingAndCaptionsTheAgent() {
        val base = state(agentSpec)
        val text = base.copy(gates = base.gates.copy(landingGreetingHeading = true))
            .landingText(agentName = "Travel Planner")
        assertThat(text.title).isNull()
        assertThat(text.caption).isEqualTo("Travel Planner")
        assertThat(text.description).isEqualTo("Plans trips")
        assertThat(text.temporaryAgentName).isEqualTo("Travel Planner")
    }

    @Test
    fun greetingHeadingKeepsTheGreetingOverABrandedSpec() {
        val base = state(modelSpec.copy(showOnLanding = true))
        val text = base.copy(gates = base.gates.copy(landingGreetingHeading = true)).landingText(agentName = null)
        assertThat(text.title).isNull()
        assertThat(text.caption).isEqualTo("Coder")
        assertThat(text.description).isEqualTo("Writes code")
    }

    @Test
    fun withoutShowOnLandingTheGreetingStaysAndThePresetGreetingDescribes() {
        val text = state(modelSpec).landingText(agentName = null)
        assertThat(text.title).isNull()
        assertThat(text.description).isEqualTo("Hi, I code")
        assertThat(text.caption).isEqualTo("Coder")
    }

    @Test
    fun showOnLandingTitlesWithTheSpecAndItsDescription() {
        val text = state(modelSpec.copy(showOnLanding = true)).landingText(agentName = null)
        assertThat(text.title).isEqualTo("Coder")
        assertThat(text.description).isEqualTo("Writes code")
        // The title already names it.
        assertThat(text.caption).isNull()
    }

    @Test
    fun anAgentSpecIsNamedByItsAgent() {
        val text = state(agentSpec).landingText(agentName = "Travel Planner")
        assertThat(text.title).isEqualTo("Travel Planner")
        assertThat(text.description).isEqualTo("Plans trips")
        assertThat(text.caption).isEqualTo("Helper Spec")
    }

    @Test
    fun aTemporaryChatGetsTheBrandedTextToo_soTheLandingSwitchesLineByLine() {
        val normal = state(modelSpec.copy(showOnLanding = true)).landingText(agentName = null)
        val temporary = state(modelSpec.copy(showOnLanding = true), temporary = true).landingText(agentName = null)
        // The flag doesn't blank the title: mid-sweep the landing still shows it, not the greeting.
        assertThat(temporary).isEqualTo(normal)
        assertThat(temporary.title).isEqualTo("Coder")
        // Once temporary chat's own title takes over, the label it replaced comes back as the caption.
        assertThat(temporary.temporaryCaption).isEqualTo("Coder")
        assertThat(temporary.temporaryAgentName).isNull()
    }

    @Test
    fun aRawModelSaysNothingNew() {
        val text = state(null).landingText(agentName = null)
        assertThat(text).isEqualTo(LandingText(caption = "gpt-5", temporaryCaption = "gpt-5"))
    }

    @Test
    fun anAgentMissingFromTheListIsCaptionedSoThePickerStillOpens() {
        val unknown = state(null).copy(
            selection = ModelSelectionState(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "gone")),
        )
        assertThat(unknown.landingText(agentName = null, agentFallbackLabel = "Agent"))
            .isEqualTo(LandingText(caption = "Agent", temporaryCaption = "Agent"))
    }

    @Test
    fun theHeroIconFollowsTheSpecChain() {
        assertThat(state(agentSpec).landingHeroIcon()).isEqualTo(LandingHeroIcon("agents", "https://av/a.png"))
        // No icon of its own: its endpoint's brand, as web's ConvoIcon draws it.
        assertThat(state(modelSpec).landingHeroIcon()).isEqualTo(LandingHeroIcon("openAI", null, ProviderId.OPENAI))
        assertThat(state(modelSpec.copy(iconURL = "groq")).landingHeroIcon())
            .isEqualTo(LandingHeroIcon("groq", null, ProviderId.GROQ))
        // An image keeps the endpoint's brand, drawn if it fails to load.
        assertThat(state(modelSpec.copy(iconURL = "/images/c.png")).landingHeroIcon())
            .isEqualTo(LandingHeroIcon("openAI", "https://srv/images/c.png", ProviderId.OPENAI))
    }

    @Test
    fun theHeroIconDrawsTheServedProviderOfACustomEndpoint() {
        val proxied = modelSpec.copy(iconURL = "Router", preset = Preset(endpoint = "Router", model = "m"))
        val state = state(proxied).let {
            it.copy(selection = it.selection.copy(endpointConfigs = mapOf("Router" to EndpointConfig(providerId = "openrouter"))))
        }
        assertThat(state.landingHeroIcon()).isEqualTo(LandingHeroIcon("Router", null, ProviderId.OPENROUTER))
    }

    @Test
    fun aRawModelDrawsItsEndpointsBrand() {
        assertThat(state(null).landingHeroIcon()).isEqualTo(LandingHeroIcon("openAI", null, ProviderId.OPENAI))
        val custom = state(null).copy(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw("Gateway", "m"),
                endpointConfigs = mapOf("Gateway" to EndpointConfig(providerId = "deepseek")),
            ),
        )
        assertThat(custom.landingHeroIcon()).isEqualTo(LandingHeroIcon("Gateway", null, ProviderId.DEEPSEEK))
    }

    @Test
    fun anAgentDrawsItsAvatarElseTheAgentsGlyph() {
        val withAvatar = state(null).copy(
            selection = ModelSelectionState(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "a1"), agents = listOf(agent)),
        )
        assertThat(withAvatar.landingHeroIcon()).isEqualTo(LandingHeroIcon(EndpointConstants.AGENTS, "https://av/a.png"))
        val unknown = state(null).copy(
            selection = ModelSelectionState(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "gone")),
        )
        assertThat(unknown.landingHeroIcon()).isEqualTo(LandingHeroIcon(EndpointConstants.AGENTS, null))
    }

    @Test
    fun aReplyInProgressGoesByItsAgentOrSpecElseItsModel() {
        assertThat(state(modelSpec).replySenderName("Assistant")).isEqualTo("Coder")
        assertThat(state(null).replySenderName("Assistant")).isEqualTo("gpt-5")
        assertThat(state(agentSpec).replySenderName("Assistant")).isEqualTo("Travel Planner")
        val noModel = state(null).copy(selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", null)))
        assertThat(noModel.replySenderName("Assistant")).isEqualTo("Assistant")
    }

    @Test
    fun nothingSelectedKeepsTheAppTile() {
        // The selection before seeding: no agent picked yet.
        val none = state(null).copy(selection = ModelSelectionState())
        assertThat(none.landingHeroIcon()).isNull()
    }
}
