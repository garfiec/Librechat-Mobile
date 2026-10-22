package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_by_author
import com.garfiec.librechat.feature.skills.resources.skill_by_you
import com.garfiec.librechat.feature.skills.resources.skill_updated
import com.garfiec.librechat.feature.skills.resources.skills_badge_always_on
import com.garfiec.librechat.feature.skills.resources.skills_badge_public
import com.garfiec.librechat.feature.skills.skillCategoryLabel
import org.jetbrains.compose.resources.stringResource

/** The name a full skill goes by on screen. */
internal val Skill.title: String
    get() = displayTitle?.takeIf { it.isNotBlank() } ?: name

private val HeaderTileSize = 48.dp

/**
 * The top of a skill's page: its tile, title, "by You · updated 2h ago · Writing", and the badges
 * for what is true of it for everyone (Public, Always on). [isPublic] is the freshest public state
 * the page knows (the sharing sheet's, once loaded), which can be newer than [skill]'s.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillDetailHeader(
    skill: Skill,
    isOwnedByViewer: Boolean,
    isPublic: Boolean,
    categories: List<Category>,
    modifier: Modifier = Modifier,
) {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val updated = remember(skill.updatedAt, format, reference) {
        skill.updatedAt?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    // "by you" is its own string: the standalone "You" doesn't decline into "by …" ("von dir", "por ti").
    val byline = if (isOwnedByViewer) {
        stringResource(Res.string.skill_by_you)
    } else {
        skill.authorName?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.skill_by_author, it) }
    }
    val meta = listOfNotNull(
        byline,
        updated?.let { stringResource(Res.string.skill_updated, it.resolve()) },
        skill.category?.takeIf { it.isNotBlank() }?.let { skillCategoryLabel(it, categories) },
    ).joinToString(" · ")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialTile(seed = skill.id, label = skill.title, size = HeaderTileSize)
        Column(Modifier.weight(1f)) {
            Text(
                text = skill.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val alwaysOn = skill.alwaysApply == true
            if (isPublic || alwaysOn) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPublic) HeaderBadge(stringResource(Res.string.skills_badge_public), Icons.Outlined.Public)
                    if (alwaysOn) HeaderBadge(stringResource(Res.string.skills_badge_always_on), Icons.Outlined.PushPin)
                }
            }
        }
    }
}

@Composable
private fun HeaderBadge(text: String, icon: ImageVector) {
    val tint = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}
