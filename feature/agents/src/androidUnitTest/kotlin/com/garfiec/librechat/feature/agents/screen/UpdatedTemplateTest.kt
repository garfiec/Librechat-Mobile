package com.garfiec.librechat.feature.agents.screen

import com.garfiec.librechat.core.common.datetime.TimestampLabel
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.resources.agent_updated_on
import com.garfiec.librechat.feature.agents.resources.agent_updated_relative
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdatedTemplateTest {

    @Test
    fun `a calendar date reads as updated on`() {
        assertThat(updatedTemplate(TimestampLabel.Text("5 Oct"))).isEqualTo(Res.string.agent_updated_on)
    }

    @Test
    fun `relative labels read without a preposition`() {
        listOf(
            TimestampLabel.JustNow,
            TimestampLabel.MinutesAgo(5),
            TimestampLabel.HoursAgo(1),
            TimestampLabel.DaysAgo(3),
        ).forEach { label ->
            assertThat(updatedTemplate(label)).isEqualTo(Res.string.agent_updated_relative)
        }
    }
}
