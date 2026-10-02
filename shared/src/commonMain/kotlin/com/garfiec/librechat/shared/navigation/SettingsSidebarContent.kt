package com.garfiec.librechat.shared.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.cd_back_to_conversations
import com.garfiec.librechat.shared.resources.settings
import org.jetbrains.compose.resources.stringResource

private val CategoryItemShape = RoundedCornerShape(12.dp)

/**
 * Settings sidebar content shown when the sidebar mode is [SidebarMode.Settings].
 */
@Composable
fun SettingsSidebarContent(
    selectedCategory: SettingsCategory?,
    onBackToConversations: () -> Unit,
    onCategorySelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            // The Liquid Glass panel already keeps the sidebar inside the safe area.
            .then(if (isLiquidGlass) Modifier else Modifier.statusBarsPadding())
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToConversations) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.cd_back_to_conversations),
                )
            }
            Text(
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
        }

        Spacer(modifier = Modifier.padding(top = 8.dp))

        SettingsCategoryItem(
            icon = Icons.Default.Settings,
            category = SettingsCategory.GENERAL,
            isSelected = selectedCategory == SettingsCategory.GENERAL,
            onClick = { onCategorySelect(SettingsCategory.GENERAL) },
        )
        SettingsCategoryItem(
            icon = Icons.AutoMirrored.Filled.Chat,
            category = SettingsCategory.CHAT,
            isSelected = selectedCategory == SettingsCategory.CHAT,
            onClick = { onCategorySelect(SettingsCategory.CHAT) },
        )
        SettingsCategoryItem(
            icon = Icons.Default.AccountCircle,
            category = SettingsCategory.ACCOUNT,
            isSelected = selectedCategory == SettingsCategory.ACCOUNT,
            onClick = { onCategorySelect(SettingsCategory.ACCOUNT) },
        )
        SettingsCategoryItem(
            icon = Icons.Default.Storage,
            category = SettingsCategory.DATA,
            isSelected = selectedCategory == SettingsCategory.DATA,
            onClick = { onCategorySelect(SettingsCategory.DATA) },
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SettingsCategoryItem(
    icon: ImageVector,
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = isLiquidGlass
    val backgroundColor = when {
        glass && isSelected -> GlassControlColors.fill
        glass -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        glass && isSelected -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (glass) CircleShape else CategoryItemShape

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(backgroundColor, shape)
            .then(if (glass) Modifier.clip(shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
    }
}
