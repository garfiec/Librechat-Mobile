package com.garfiec.librechat.feature.conversations.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.feature.conversations.resources.*
import com.garfiec.librechat.feature.conversations.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterBar(
    tags: List<ConversationTag>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    if (tags.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = if (expanded) stringResource(Res.string.cd_hide_tags) else stringResource(Res.string.cd_show_tags),
                    tint = if (selectedTags.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (selectedTags.isNotEmpty() && !expanded) {
                Text(
                    text = stringResource(Res.string.tags_active_count, selectedTags.size, if (selectedTags.size > 1) "s" else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedTags.isNotEmpty()) {
                    AdaptiveFilterChip(
                        selected = false,
                        onClick = onClearFilter,
                        label = { Text(stringResource(Res.string.clear)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.cd_clear_filter),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                tags.forEach { tag ->
                    val tagName = tag.tag ?: return@forEach
                    AdaptiveFilterChip(
                        selected = tagName in selectedTags,
                        onClick = { onTagToggle(tagName) },
                        label = {
                            Text("$tagName (${tag.count})")
                        },
                    )
                }
            }
        }
    }
}
