package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
                // Every slot keeps its height when empty, so cards in a grid row line up.
                Text(
                    text = skill.description?.trim().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SkillMetaLine {
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

/**
 * The meta row: one line, there even when empty. Items that don't fit are dropped from the end
 * rather than wrapped, so the badges stay and owner, category and date give way first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillMetaLine(content: @Composable FlowRowScope.() -> Unit) {
    Box(Modifier.padding(top = 8.dp).heightIn(min = MetaIconSize), contentAlignment = Alignment.CenterStart) {
        // Holds a text line's height when no item is shown.
        Text(" ", style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.alpha(0f))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxLines = 1,
            content = content,
        )
    }
}

@Composable
private fun SkillMetaItem(
    text: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MetaIconSize))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** First-load stand-in that mirrors the card's slots, so the grid doesn't jump on arrival. */
@Composable
internal fun SkillCardPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val titleLine = with(density) { typography.titleMedium.lineHeight.toDp() }
    val descriptionLine = with(density) { typography.bodyMedium.lineHeight.toDp() }
    val metaLine = maxOf(with(density) { typography.labelMedium.lineHeight.toDp() }, MetaIconSize)
    AdaptiveCard(
        modifier = modifier.fillMaxWidth(),
        shape = SkillCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(block))
            Column(modifier = Modifier.weight(1f)) {
                PlaceholderLine(block, lineHeight = titleLine, barHeight = 16.dp, widthFraction = 0.5f)
                Spacer(Modifier.height(4.dp))
                PlaceholderLine(block, lineHeight = descriptionLine, barHeight = 12.dp, widthFraction = 0.9f)
                PlaceholderLine(block, lineHeight = descriptionLine, barHeight = 12.dp, widthFraction = 0.6f)
                Spacer(Modifier.height(8.dp))
                PlaceholderLine(block, lineHeight = metaLine, barHeight = 12.dp, widthFraction = 0.3f)
            }
        }
    }
}

@Composable
private fun PlaceholderLine(color: Color, lineHeight: Dp, barHeight: Dp, widthFraction: Float) {
    Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(widthFraction).height(barHeight).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

private val SkillCardShape = RoundedCornerShape(16.dp)

/** The meta row's icons; the row is never shorter than them. */
private val MetaIconSize = 16.dp
