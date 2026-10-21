package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.feature.agents.AgentContactDisplayData
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

class AgentDisplayMappersTest {

    private fun contact(name: String? = null, email: String? = null) = buildJsonObject {
        name?.let { put("name", JsonPrimitive(it)) }
        email?.let { put("email", JsonPrimitive(it)) }
    }

    @Test
    fun `the agent's own support contact wins over the owner`() {
        val agent = Agent(
            id = "a",
            supportContact = contact(name = "Help desk", email = "help@example.com"),
            ownerContact = contact(name = "Dana"),
        )

        assertThat(agent.resolvedContact()).isEqualTo(AgentContactDisplayData("Help desk", "help@example.com"))
        assertThat(agent.toCardDisplayData(BASE).contactName).isEqualTo("Help desk")
    }

    @Test
    fun `a support contact with only an email shows the email on the card`() {
        val agent = Agent(
            id = "a",
            supportContact = contact(email = " help@example.com "),
            ownerContact = contact(name = "Dana"),
        )

        assertThat(agent.toCardDisplayData(BASE).contactName).isEqualTo("help@example.com")
    }

    @Test
    fun `an empty support contact falls back to the owner`() {
        val agent = Agent(
            id = "a",
            supportContact = contact(name = " ", email = ""),
            ownerContact = contact(name = "Dana"),
        )

        assertThat(agent.resolvedContact()).isEqualTo(AgentContactDisplayData("Dana", null))
    }

    @Test
    fun `the raw author id is never a contact`() {
        val agent = Agent(id = "a", author = "6ad0e1a7db029854749d8540", authorName = null)

        assertThat(agent.resolvedContact()).isNull()
        assertThat(agent.toCardDisplayData(BASE).contactName).isNull()
    }

    @Test
    fun `a blank description and category read as absent`() {
        val card = Agent(id = "a", description = "<p> </p>", category = " ").toCardDisplayData(BASE)

        assertThat(card.description).isNull()
        assertThat(card.categoryValue).isNull()
    }

    private companion object {
        const val BASE = "https://chat.example.com"
    }
}
