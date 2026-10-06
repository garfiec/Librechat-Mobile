package com.garfiec.librechat.core.ui.contextusage

import com.garfiec.librechat.core.model.usage.BreakdownCategory
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.CostRows
import com.garfiec.librechat.core.model.usage.buildContextBreakdown
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.model.usage.visibleBreakdown
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The Settings previews draw [SampleContextModel]; pin what each preset shows of it. */
class SampleContextModelTest {

    private val model = SampleContextModel
    private val view = buildContextBreakdown(
        model.usage,
        model.totals.estimate,
        model.compactionAvailable,
        model.totals.compactionReclaim,
    )

    private fun visible(preset: ContextDetailPreset) =
        visibleBreakdown(view, preset.sections, model.totals, model.costEnabled)

    @Test
    fun the_sample_reads_about_eleven_percent() {
        assertThat(percentOf(view.usedTokens, view.windowTokens)).isEqualTo(11)
    }

    @Test
    fun simple_shows_used_and_free_and_nothing_below() {
        val simple = visible(ContextDetailPreset.SIMPLE)

        assertThat(simple.usedAndFreeOnly).isTrue()
        assertThat(simple.totals).isNull()
        assertThat(simple.cost).isNull()
    }

    @Test
    fun standard_shows_categories_with_one_tools_row_and_the_conversation_cost() {
        val standard = visible(ContextDetailPreset.STANDARD)

        assertThat(standard.rows.map { it.category }).containsExactly(
            BreakdownCategory.MESSAGES,
            BreakdownCategory.TOOL_CALLS,
            BreakdownCategory.SYSTEM,
            BreakdownCategory.TOOLS_ALL,
        ).inOrder()
        assertThat(standard.cost).isEqualTo(CostRows(3.89, primaryIsWholeConversation = true, allBranches = null))
    }

    @Test
    fun detailed_shows_tool_types_cache_totals_and_both_costs() {
        val detailed = visible(ContextDetailPreset.DETAILED)

        assertThat(detailed.rows.map { it.category }).contains(BreakdownCategory.SKILLS)
        assertThat(detailed.cacheRead).isEqualTo(82_100)
        assertThat(detailed.totals?.output).isEqualTo(91_100)
        assertThat(detailed.cost).isEqualTo(CostRows(2.60, primaryIsWholeConversation = false, allBranches = 3.89))
    }
}
