package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.LiquidSegmentedControl
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/** Which M3 tab row [AdaptiveTabRow] draws in Material. */
enum class AdaptiveTabRowKind {
    /** Top-level destinations: `PrimaryTabRow`. */
    PRIMARY,

    /** Tabs inside a section, under a top bar: `SecondaryTabRow`. */
    SECONDARY,
}

/**
 * The iOS segmented control in Liquid Glass. Pass [backdrop] only from a bar slot: a tab row inside
 * the recorded body must not sample its own ancestor.
 */
@Composable
fun AdaptiveTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    kind: AdaptiveTabRowKind = AdaptiveTabRowKind.PRIMARY,
    backdrop: GlassBackdrop? = null,
) {
    if (isLiquidGlass) {
        LiquidSegmentedControl(
            options = titles,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            backdrop = backdrop,
        )
        return
    }
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
    when (kind) {
        AdaptiveTabRowKind.PRIMARY -> PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier, tabs = tabs)
        AdaptiveTabRowKind.SECONDARY -> SecondaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier, tabs = tabs)
    }
}
