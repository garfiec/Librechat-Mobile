package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader

/**
 * A titled pill choice with an optional line under it. It sits on the page rather than in a cell: a cell
 * clips to its rounded shape, and the glass thumb's swell overhangs the control further than a cell's padding.
 */
@Composable
internal fun PillGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AdaptiveSectionHeader(label)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptivePillChoice(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
