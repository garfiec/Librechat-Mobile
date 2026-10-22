package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_file_count
import com.garfiec.librechat.feature.skills.resources.skills_badge_always_on
import com.garfiec.librechat.feature.skills.resources.skills_badge_off
import com.garfiec.librechat.feature.skills.resources.skills_badge_public
import com.garfiec.librechat.feature.skills.resources.skills_owner_you
import com.garfiec.librechat.feature.skills.skillCategoryLabel
import com.garfiec.librechat.feature.skills.viewmodel.SkillRowUi
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The name a skill goes by on screen. */
internal val SkillRowUi.title: String
    get() = summary.displayTitle?.takeIf { it.isNotBlank() } ?: summary.name

/**
 * One skill in the list: its tile, title, description, and a meta row with what is true of it for
 * the viewer — Off (inactive for them), Always on, Public — then its files, owner, category and
 * when it last changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillCard(
    row: SkillRowUi,
    categories: List<Category>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skill = row.summary
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val updated = remember(skill.updatedAt, format, reference) {
        skill.updatedAt?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    val owner = if (row.isOwnedByViewer) {
        stringResource(Res.string.skills_owner_you)
    } else {
        skill.authorName?.takeIf { it.isNotBlank() }
    }
    val category = skill.category?.takeIf { it.isNotBlank() }?.let { skillCategoryLabel(it, categories) }

    AdaptiveCard(
        onClick = onClick,
        // No contentDescription override: it would replace the merged text, so the description and
        // the meta row would go unread.
        modifier = modifier.fillMaxWidth(),
        shape = SkillCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialTile(seed = skill.id, label = row.title)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = skill.description?.trim().orEmpty()
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // Only a known "off": while the states map is loading the badge stays away.
                    if (row.isActive == false) {
                        SkillMetaItem(stringResource(Res.string.skills_badge_off), Icons.Outlined.Block, statusColors().neutral)
                    }
                    if (skill.alwaysApply == true) {
                        SkillMetaItem(
                            stringResource(Res.string.skills_badge_always_on),
                            Icons.Outlined.PushPin,
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (skill.isPublic == true) {
                        SkillMetaItem(
                            stringResource(Res.string.skills_badge_public),
                            Icons.Outlined.Public,
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (skill.fileCount > 0) {
                        SkillMetaItem(pluralStringResource(Res.plurals.skill_file_count, skill.fileCount, skill.fileCount))
                    }
                    if (owner != null) SkillMetaItem(owner, Icons.Outlined.Person)
                    if (category != null) SkillMetaItem(category)
                    if (updated != null) SkillMetaItem(updated.resolve())
                }
            }
        }
    }
}

@Composable
private fun SkillMetaItem(
    text: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** First-load stand-in, sized like a card with a description, so the grid doesn't jump on arrival. */
@Composable
internal fun SkillCardPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        shape = SkillCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
                Box(Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
            }
        }
    }
}

private val SkillCardShape = RoundedCornerShape(16.dp)
