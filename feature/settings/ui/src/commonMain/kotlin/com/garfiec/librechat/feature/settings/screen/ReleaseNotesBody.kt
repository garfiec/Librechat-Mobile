package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.AppRelease
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.whats_new_full_changelog
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import org.jetbrains.compose.resources.stringResource

/** One release's version, date, Highlights and collapsible full changelog. */
@Composable
internal fun ReleaseNotesBody(release: AppRelease) {
    // The toggles' ripples bleed into this inset; everything else pads back to 16dp.
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = TOGGLE_BLEED, vertical = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = TOGGLE_BLEED),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = release.version,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ReleaseDate(release)
        }
        ReleaseNotesDetails(release)
    }
}

/** A past release: its version and date, expanding on tap to the notes [ReleaseNotesBody] shows. */
@Composable
internal fun CollapsedReleaseNotes(release: AppRelease) {
    var expanded by rememberSaveable(release.tag) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = TOGGLE_BLEED, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = TOGGLE_BLEED, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = release.version,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ReleaseDate(release)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            ReleaseNotesDetails(release, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun ReleaseDate(release: AppRelease) {
    // ISO date: unambiguous in every locale, and GitHub only reports UTC anyway.
    release.publishedAt?.take(ISO_DATE_LENGTH)?.let { date ->
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseNotesDetails(release: AppRelease, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        release.highlights?.let { highlights ->
            Spacer(modifier = Modifier.height(8.dp))
            NotesMarkdown(highlights, Modifier.padding(horizontal = TOGGLE_BLEED))
        }
        release.fullChangelog?.let { changelog ->
            Spacer(modifier = Modifier.height(8.dp))
            FullChangelog(changelog, initiallyExpanded = release.highlights == null)
        }
    }
}

@Composable
private fun FullChangelog(changelog: String, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = TOGGLE_BLEED, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.whats_new_full_changelog),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            NotesMarkdown(changelog, Modifier.padding(horizontal = TOGGLE_BLEED))
        }
    }
}

@Composable
private fun NotesMarkdown(markdown: String, modifier: Modifier = Modifier) {
    // Parsed synchronously: the default async parse composes a 0-height slot first, so a lazy item
    // scrolled back into view grows a frame later and the list jumps. Release notes are a few KB.
    val parsed by rememberMarkdownState(content = markdown, immediate = true).state.collectAsState()
    Markdown(
        state = parsed,
        colors = markdownColor(),
        typography = markdownTypography(),
        modifier = modifier.fillMaxWidth(),
    )
}

private const val ISO_DATE_LENGTH = 10
private val TOGGLE_BLEED = 8.dp
