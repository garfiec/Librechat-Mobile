package com.garfiec.librechat.feature.chat.prompts.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class PromptSortOrder(val label: StringResource) {
    RECENT(Res.string.prompt_sort_recent),
    POPULAR(Res.string.prompt_sort_popular),
    ALPHABETICAL(Res.string.prompt_sort_alphabetical),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptFilterSheet(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    sortOrder: PromptSortOrder,
    onSortOrderChange: (PromptSortOrder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        PromptFilterSheetContent(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelect = onCategorySelect,
            sortOrder = sortOrder,
            onSortOrderChange = onSortOrderChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptFilterSheetContent(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    sortOrder: PromptSortOrder,
    onSortOrderChange: (PromptSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(Res.string.prompt_filter_title),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category section
        Text(
            text = stringResource(Res.string.label_category),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AdaptiveFilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = { Text(stringResource(Res.string.label_all)) },
            )
            categories.forEach { category ->
                AdaptiveFilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        onCategorySelect(
                            if (selectedCategory == category) null else category,
                        )
                    },
                    label = { Text(category) },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sort section
        Text(
            text = stringResource(Res.string.label_sort_by),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PromptSortOrder.entries.forEach { order ->
                AdaptiveFilterChip(
                    selected = sortOrder == order,
                    onClick = { onSortOrderChange(order) },
                    label = { Text(stringResource(order.label)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
