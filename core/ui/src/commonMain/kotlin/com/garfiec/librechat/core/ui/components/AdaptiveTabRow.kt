package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * The tab an [AdaptiveTabRow] over this pager shows selected: the page under the finger while it's
 * dragged, otherwise the page it is settling on. A tapped tab stays selected while the pager scrolls
 * to it, rather than stepping through the pages in between (which [PagerState.currentPage] does).
 */
@Composable
fun PagerState.selectedTabIndex(): Int {
    val dragged by interactionSource.collectIsDraggedAsState()
    return if (dragged) currentPage else targetPage
}

/**
 * The iOS segmented control in Liquid Glass. Pass [backdrop] only from a bar slot: a tab row inside
 * the recorded body must not sample its own ancestor.
 *
 * Tabs share the width evenly while every title fits its share on one line. When one doesn't, the
 * Material row scrolls with each tab as wide as its title, as long as every title fits half the row
 * (so two tabs always show at once), and the glass control wraps titles onto a second line. A title
 * still too long is cut short as [titleOverflow] says: a middle ellipsis keeps titles that differ only
 * at the end (model ids) apart.
 *
 * The fit is decided on [fitTitles], which defaults to the titles themselves. A row whose titles change
 * as the user types (live counts) passes stable worst-case titles there, so the layout holds.
 */
@Composable
fun AdaptiveTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    kind: AdaptiveTabRowKind = AdaptiveTabRowKind.PRIMARY,
    backdrop: GlassBackdrop? = null,
    titleOverflow: TextOverflow = TextOverflow.Ellipsis,
    fitTitles: List<String> = titles,
) {
    if (isLiquidGlass) {
        LiquidSegmentedControl(
            options = titles,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            backdrop = backdrop,
            allowTwoLines = true,
            labelOverflow = titleOverflow,
            fitLabels = fitTitles,
        )
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val style = MaterialTheme.typography.titleSmall
        val shareWidth = maxWidth / titles.size.coerceAtLeast(1) - TabPadding * 2
        val share = rememberChoiceLabelFit(fitTitles, style, shareWidth, allowTwoLines = false)
        val half = rememberChoiceLabelFit(fitTitles, style, maxWidth / 2 - TabPadding * 2, allowTwoLines = false)
        MaterialTabRow(titles, selectedIndex, onSelect, kind, scrollable = !share.fits && half.fits, titleOverflow)
    }
}

@Composable
private fun MaterialTabRow(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    kind: AdaptiveTabRowKind,
    scrollable: Boolean,
    titleOverflow: TextOverflow,
) {
    val tabs: @Composable () -> Unit = {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { Text(text = title, maxLines = 1, overflow = titleOverflow) },
            )
        }
    }
    val modifier = Modifier.fillMaxWidth()
    when {
        // No edge inset: the first tab starts where the fixed row's does.
        scrollable && kind == AdaptiveTabRowKind.PRIMARY ->
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, modifier = modifier, edgePadding = 0.dp, tabs = tabs)
        scrollable ->
            SecondaryScrollableTabRow(selectedTabIndex = selectedIndex, modifier = modifier, edgePadding = 0.dp, tabs = tabs)
        kind == AdaptiveTabRowKind.PRIMARY -> PrimaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier, tabs = tabs)
        else -> SecondaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier, tabs = tabs)
    }
}

// M3's Tab pads its text 16dp each side.
private val TabPadding = 16.dp
