package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.config.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationStartersTest {

    private val spec = ModelSpec(name = "s", conversationStarters = listOf("From the spec"))

    @Test
    fun theAgentsOwnStartersWinOverTheSpecs() {
        assertEquals(listOf("From the agent"), resolveLandingStarters(isAgent = true, listOf("From the agent"), spec))
    }

    @Test
    fun anAgentWithNoneFallsBackToTheSpec() {
        assertEquals(listOf("From the spec"), resolveLandingStarters(isAgent = true, emptyList(), spec))
    }

    @Test
    fun anUnresolvedAgentOffersNothingRatherThanTheSpecsForNow() {
        assertEquals(emptyList(), resolveLandingStarters(isAgent = true, agentStarters = null, spec = spec))
    }

    @Test
    fun aRawModelSpecOffersItsOwn() {
        assertEquals(listOf("From the spec"), resolveLandingStarters(isAgent = false, agentStarters = null, spec = spec))
    }

    @Test
    fun noAgentAndNoSpecOffersNothing() {
        assertEquals(emptyList(), resolveLandingStarters(isAgent = false, agentStarters = null, spec = null))
    }

    @Test
    fun startersAreTrimmedBlanksDroppedAndCappedAtFour() {
        val agent = listOf(" one ", "", "two", "   ", "three", "four", "five")
        assertEquals(listOf("one", "two", "three", "four"), resolveLandingStarters(isAgent = true, agent, spec = null))
    }

    @Test
    fun onlyBlankAgentStartersFallBackToTheSpec() {
        assertEquals(listOf("From the spec"), resolveLandingStarters(isAgent = true, listOf(" "), spec))
    }
}
