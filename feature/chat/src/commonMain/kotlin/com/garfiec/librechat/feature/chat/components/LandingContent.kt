package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun LandingContent(
    selectedModel: String?,
    modifier: Modifier = Modifier,
    selectedAgentName: String? = null,
    /** Null when the server offers no picker: the label is then plain text. */
    onSelectModel: (() -> Unit)? = null,
    /**
     * The project the new chat will be filed into. A label, not a control: the way out is a plain
     * New chat.
     */
    projectName: String? = null,
    /** The agent's or spec's own icon, as web's landing shows it. Null keeps the app tile. */
    heroIcon: LandingHeroIcon? = null,
    /** Off while starters are on screen: they already say what the subtitle asks. */
    showSubtitle: Boolean = true,
    /** Temporary chat is on: the greeting says so, and the subtitle says it won't be saved. */
    isTemporaryChat: Boolean = false,
    /** Room for whatever is docked above the composer, so the hero centres in what is left. */
    bottomPadding: Dp = 120.dp,
) {
    val timeGreeting = remember { getTimeBasedGreeting() }
    val greeting = stringResource(if (isTemporaryChat) Res.string.temporary_chat_title else timeGreeting)
    val selectModelLabel = stringResource(Res.string.cd_select_model)
    val selectableModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .then(
            if (onSelectModel != null) {
                Modifier.clickable(onClickLabel = selectModelLabel, role = Role.Button, onClick = onSelectModel)
            } else {
                Modifier
            },
        )
        .padding(horizontal = 12.dp, vertical = 8.dp)

    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // With the keyboard up and starters docked above the composer, the full hero no longer fits
        // between the top bar and them: drop the icon and shrink the greeting rather than overlap.
        val compact = maxHeight - bottomPadding < CompactHeroBelow
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolls only once it overflows (large text with the keyboard up): a scrollable takes every
                // vertical drag, and on a landing that fits the drag has to reach the pull-up tools sheet.
                .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                .padding(start = 24.dp, end = 24.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (compact) {
                // No icon: the greeting leads.
            } else if (heroIcon != null) {
                EndpointIcon(
                    endpointName = heroIcon.endpointName,
                    iconUrl = heroIcon.iconUrl,
                    size = 56.dp,
                    glyphTint = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = stringResource(Res.string.cd_librechat),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (!compact) Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = greeting,
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )

            // Starters don't say the chat won't be saved, so the temporary subtitle stays with them.
            if (showSubtitle || isTemporaryChat) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (isTemporaryChat) Res.string.temporary_chat_subtitle else Res.string.how_can_i_help,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Agent name display (prominent when an agent is selected)
            if (!selectedAgentName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedAgentName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = selectableModifier,
                )
            } else if (selectedModel != null) {
                Text(
                    text = selectedModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = selectableModifier,
                )
            }

            if (projectName != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.chat_in_project, projectName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The full hero's height with the subtitle hidden: icon, gap, greeting and the model label. */
private val CompactHeroBelow = 200.dp

/**
 * The landing's hero icon: [iconUrl] when it loads, else [endpointName]'s glyph. A spec's `iconURL`
 * may name an endpoint rather than an image, which is why both exist.
 */
data class LandingHeroIcon(val endpointName: String?, val iconUrl: String?)

/**
 * Returns a time-based greeting with 6 periods matching the web frontend:
 * early morning, morning, afternoon, evening, late night, and weekend variants.
 */
private fun getTimeBasedGreeting(): StringResource {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour
    val dayOfWeek = now.dayOfWeek
    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    if (isWeekend) {
        return when {
            hour < 5 -> Res.string.greeting_late_night
            hour < 12 -> Res.string.greeting_weekend_morning
            hour < 17 -> Res.string.greeting_weekend_afternoon
            else -> Res.string.greeting_weekend_evening
        }
    }

    return when {
        hour < 5 -> Res.string.greeting_late_night
        hour < 9 -> Res.string.greeting_early_morning
        hour < 12 -> Res.string.greeting_morning
        hour < 17 -> Res.string.greeting_afternoon
        else -> Res.string.greeting_evening
    }
}
