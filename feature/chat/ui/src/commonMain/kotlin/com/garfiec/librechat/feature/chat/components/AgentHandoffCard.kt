package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Data class representing an agent handoff event.
 */
data class AgentHandoff(
    val fromAgent: String?,
    val toAgent: String?,
    val reason: String?,
)

/**
 * Card displaying an agent handoff as a timeline:
 * [FromAgent] -> arrow -> [ToAgent] with reason text below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentHandoffCard(
    handoff: AgentHandoff,
    modifier: Modifier = Modifier,
) {
    val unknownAgent = stringResource(Res.string.agent_unknown)
    val handoffCd =
        stringResource(Res.string.cd_agent_handoff, handoff.fromAgent ?: unknownAgent, handoff.toAgent ?: unknownAgent)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = handoffCd
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Timeline row: [From] -> [To]. The arrow travels with the "to" label, so when the
            // pair doesn't fit on one line it moves under the "from" label as a whole.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // From agent
                AgentLabel(
                    name = handoff.fromAgent ?: stringResource(Res.string.agent_previous),
                    modifier = Modifier.weight(1f),
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Arrow
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = stringResource(Res.string.cd_handed_off_to),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )

                    // To agent
                    AgentLabel(name = handoff.toAgent ?: stringResource(Res.string.agent_next))
                }
            }

            // Reason text
            if (!handoff.reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = handoff.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AgentLabel(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
