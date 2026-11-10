package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Subject
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoiceOverhang
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ArtifactLibraryChoice
import org.jetbrains.compose.resources.stringResource

private val DECISIONS = listOf(Decision.ASK, Decision.ALLOW, Decision.DECLINE)

/** The Libraries row's value: how many flagged libraries have a device-wide answer. */
@Composable
internal fun artifactLibrariesSummary(choices: List<ArtifactLibraryChoice>?): String {
    val set = choices?.count { it.decision != Decision.ASK } ?: 0
    return if (choices.isNullOrEmpty() || set == 0) {
        stringResource(Res.string.artifact_libraries_none_set)
    } else {
        stringResource(Res.string.artifact_libraries_some_set, set, choices.size)
    }
}

/**
 * Sets, for each flagged library, whether artifacts load it, skip it, or ask. An answer set here
 * is the same one "Remember" saves from an artifact's prompt.
 */
@Composable
internal fun ArtifactLibrariesDialog(
    choices: List<ArtifactLibraryChoice>?,
    onDecisionChange: (Subject, Decision) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(Res.string.artifact_libraries_ask),
        stringResource(Res.string.artifact_libraries_allow),
        stringResource(Res.string.artifact_libraries_decline),
    )
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        scrollableText = false,
        modifier = modifier,
        title = { Text(stringResource(Res.string.artifact_libraries_title)) },
        text = {
            val direction = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // The scroll clips, so leave room for the glass thumb's swell past the sides and the
                    // last row.
                    .padding(
                        start = AdaptiveSegmentedChoiceOverhang.calculateStartPadding(direction),
                        end = AdaptiveSegmentedChoiceOverhang.calculateEndPadding(direction),
                        bottom = AdaptiveSegmentedChoiceOverhang.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.artifact_libraries_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choices.orEmpty().forEach { choice ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(choice.subject.library, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = advisoryLabel(choice.subject.advisory),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AdaptiveSegmentedChoice(
                            options = labels,
                            selectedIndex = DECISIONS.indexOf(choice.decision),
                            onSelect = { onDecisionChange(choice.subject, DECISIONS[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_done))
            }
        },
    )
}

@Composable
private fun advisoryLabel(advisory: String): String = when (advisory) {
    "deprecated" -> stringResource(Res.string.artifact_libraries_deprecated)
    "vulnerable" -> stringResource(Res.string.artifact_libraries_vulnerable)
    else -> stringResource(Res.string.artifact_libraries_unmaintained)
}
