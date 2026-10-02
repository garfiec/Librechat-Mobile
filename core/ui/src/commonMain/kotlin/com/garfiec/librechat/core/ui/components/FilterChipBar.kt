package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Generic horizontal scrolling row of FilterChips.
 *
 * @param items All items to display as chips.
 * @param isSelected Determines whether an item is currently selected.
 * @param onSelect Called when a chip is clicked.
 * @param label Extracts a display label from each item.
 */
@Composable
fun <T : Any> FilterChipBar(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            AdaptiveFilterChip(
                selected = isSelected(item),
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
            )
        }
    }
}
