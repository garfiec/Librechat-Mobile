package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.COMPACT_NUDGE_THRESHOLDS
import com.garfiec.librechat.core.model.usage.ContextCostScope
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.contextusage.ContextBreakdownCard
import com.garfiec.librechat.core.ui.contextusage.SampleContextModel
import com.garfiec.librechat.core.ui.contextusage.pressureColor
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Chat → Context usage: where the context gauge shows, how much of its breakdown it
 * shows (with a live preview drawn by the same card the chat uses), and when compacting is
 * suggested.
 */
@Composable
fun ContextUsageSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContextUsageSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingPlacement by remember { mutableStateOf(false) }

    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.context_bar_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "where_header") { AdaptiveSectionHeader(stringResource(Res.string.context_settings_where)) }
                adaptiveSection {
                    row(key = "placement") {
                        SelectorRow(
                            title = stringResource(Res.string.context_settings_placement),
                            value = contextBarPlacementLabel(state.placement),
                            onClick = { choosingPlacement = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                item(key = "detail_header") { AdaptiveSectionHeader(stringResource(Res.string.context_settings_detail)) }
                adaptiveSection {
                    row(key = "detail") {
                        DetailControls(
                            state = state,
                            onPreset = viewModel::setPreset,
                            onAdvancedChange = viewModel::setAdvanced,
                            onSections = viewModel::updateSections,
                        )
                    }
                }

                item(key = "compact_header") { AdaptiveSectionHeader(stringResource(Res.string.context_settings_compacting)) }
                adaptiveSection {
                    row(key = "compact") {
                        CompactControls(threshold = state.compactNudgeThreshold, onThreshold = viewModel::setCompactNudgeThreshold)
                    }
                }
            }
        }
    }

    if (choosingPlacement) {
        RadioSelectionDialog(
            title = stringResource(Res.string.context_bar_title),
            description = stringResource(Res.string.context_bar_desc),
            options = ContextBarPlacement.entries,
            selected = state.placement,
            onSave = {
                viewModel.setPlacement(it)
                choosingPlacement = false
            },
            onDismiss = { choosingPlacement = false },
            optionLabel = { contextBarPlacementLabel(it) },
        )
    }
}

@Composable
private fun DetailControls(
    state: ContextUsageSettingsUiState,
    onPreset: (ContextDetailPreset) -> Unit,
    onAdvancedChange: (Boolean) -> Unit,
    onSections: ((ContextDetailSections) -> ContextDetailSections) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AdaptivePillChoice(
            options = ContextDetailPreset.entries.map { presetLabel(it) },
            selectedIndex = ContextDetailPreset.entries.indexOf(state.preset),
            onSelect = { onPreset(ContextDetailPreset.entries[it]) },
            enabled = !state.advanced,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(if (state.advanced) Res.string.context_settings_custom_in_use else presetDescription(state.preset)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GroupLabel(stringResource(Res.string.context_settings_preview))
        AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
            ContextBreakdownCard(
                model = SampleContextModel,
                sections = state.sections,
                modifier = Modifier.padding(16.dp),
            )
        }

        ToggleRow(
            title = stringResource(Res.string.context_settings_advanced),
            description = stringResource(Res.string.context_settings_advanced_desc),
            checked = state.advanced,
            onChange = onAdvancedChange,
        )
        if (state.advanced) {
            CustomSections(state, onSections)
        }
    }
}

@Composable
private fun CustomSections(
    state: ContextUsageSettingsUiState,
    onSections: ((ContextDetailSections) -> ContextDetailSections) -> Unit,
) {
    val sections = state.sections
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleRow(
            title = stringResource(Res.string.context_section_breakdown),
            description = stringResource(Res.string.context_section_breakdown_desc),
            checked = sections.breakdown,
            onChange = { on -> onSections { it.copy(breakdown = on) } },
        )
        ToggleRow(
            title = stringResource(Res.string.context_section_tool_types),
            description = stringResource(Res.string.context_section_tool_types_desc),
            checked = sections.toolTypes,
            onChange = { on -> onSections { it.copy(toolTypes = on) } },
            enabled = sections.breakdown,
        )
        ToggleRow(
            title = stringResource(Res.string.context_section_cache),
            description = stringResource(Res.string.context_section_cache_desc),
            checked = sections.cacheDetail,
            onChange = { on -> onSections { it.copy(cacheDetail = on) } },
            enabled = sections.breakdown,
        )
        ToggleRow(
            title = stringResource(Res.string.context_section_insights),
            description = stringResource(Res.string.context_section_insights_desc),
            checked = sections.insights,
            onChange = { on -> onSections { it.copy(insights = on) } },
        )
        ToggleRow(
            title = stringResource(Res.string.context_section_totals),
            description = stringResource(Res.string.context_section_totals_desc),
            checked = sections.totals,
            onChange = { on -> onSections { it.copy(totals = on) } },
        )
        Text(text = stringResource(Res.string.context_section_cost), style = MaterialTheme.typography.bodyLarge)
        AdaptivePillChoice(
            options = ContextCostScope.entries.map { costScopeLabel(it) },
            selectedIndex = ContextCostScope.entries.indexOf(sections.cost),
            onSelect = { index -> onSections { it.copy(cost = ContextCostScope.entries[index]) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.serverReportsCost) {
            Text(
                text = stringResource(Res.string.context_section_cost_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactControls(threshold: Int, onThreshold: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(Res.string.context_settings_compact_at), style = MaterialTheme.typography.bodyLarge)
        AdaptivePillChoice(
            options = COMPACT_NUDGE_THRESHOLDS.map { if (it == 0) stringResource(Res.string.context_settings_compact_off) else "$it%" },
            selectedIndex = COMPACT_NUDGE_THRESHOLDS.indexOf(threshold).coerceAtLeast(0),
            onSelect = { onThreshold(COMPACT_NUDGE_THRESHOLDS[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(Res.string.context_settings_compact_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupLabel(stringResource(Res.string.context_settings_preview))
        NudgePreview(threshold)
    }
}

/**
 * The composer's context pill a little past the chosen threshold: tonal, with the compact control
 * the chat shows. With the suggestion off, the plain pill at the same reading.
 */
@Composable
private fun NudgePreview(threshold: Int) {
    val percent = (if (threshold > 0) threshold else DEFAULT_PREVIEW_THRESHOLD) + PREVIEW_OVERSHOOT
    val suggested = threshold > 0
    val tint = pressureColor(percent)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (suggested) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.context_settings_pill_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.width(96.dp),
                color = tint ?: MaterialTheme.colorScheme.primary,
                drawStopIndicator = {},
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
            )
            if (suggested) {
                Icon(Icons.Outlined.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun presetLabel(preset: ContextDetailPreset): String = stringResource(
    when (preset) {
        ContextDetailPreset.SIMPLE -> Res.string.context_preset_simple
        ContextDetailPreset.STANDARD -> Res.string.context_preset_standard
        ContextDetailPreset.DETAILED -> Res.string.context_preset_detailed
    },
)

private fun presetDescription(preset: ContextDetailPreset) = when (preset) {
    ContextDetailPreset.SIMPLE -> Res.string.context_preset_simple_desc
    ContextDetailPreset.STANDARD -> Res.string.context_preset_standard_desc
    ContextDetailPreset.DETAILED -> Res.string.context_preset_detailed_desc
}

@Composable
private fun costScopeLabel(scope: ContextCostScope): String = stringResource(
    when (scope) {
        ContextCostScope.OFF -> Res.string.context_cost_off
        ContextCostScope.WHOLE_CONVERSATION -> Res.string.context_cost_whole
        ContextCostScope.BOTH -> Res.string.context_cost_both
    },
)

/** The chat settings row's summary: where the gauge shows, then the detail level. */
@Composable
internal fun contextUsageSummary(placement: ContextBarPlacement, preset: ContextDetailPreset, advanced: Boolean): String {
    if (placement == ContextBarPlacement.HIDDEN) return contextBarPlacementLabel(placement)
    val detail = if (advanced) stringResource(Res.string.context_preset_custom) else presetLabel(preset)
    return stringResource(Res.string.context_settings_summary, contextBarPlacementLabel(placement), detail)
}

private const val DEFAULT_PREVIEW_THRESHOLD = 70
private const val PREVIEW_OVERSHOOT = 2
