package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_change_first_model
import com.garfiec.librechat.feature.chat.resources.cd_change_second_model
import com.garfiec.librechat.feature.chat.resources.compare_also_asking
import org.jetbrains.compose.resources.stringResource

/** The composer's view of an active comparison. */
sealed interface ComposerComparison {
    /**
     * A phone tab: the model the composer is NOT already showing (B on the A tab, A on the B tab),
     * as the "Also asking" row above the field.
     */
    @Immutable
    data class OtherModel(
        val lane: ComparisonLane,
        val label: String,
        val onChangeModel: () -> Unit,
    ) : ComposerComparison

    /** Side by side: both models, as the A·B badge pair inside the field. */
    @Immutable
    data class BothModels(
        val primaryLabel: String,
        val secondaryLabel: String,
        val onChangePrimary: () -> Unit,
        val onChangeSecondary: () -> Unit,
    ) : ComposerComparison
}

/** What the composer's placeholder says about Compare. */
enum class ComposerComparisonHint {
    /** Not comparing, or a phone tab: "Message <model>". */
    NONE,

    /** Setup is choosing model B; sends wait for it. */
    SETTING_UP,

    /** Both panes side by side: one message goes to both. */
    BOTH,
}

/**
 * "Also asking [model ▾]" inside the composer while comparing. The chip changes that model. Turning
 * Compare off lives where it was turned on, in the overflow menu.
 */
@Composable
internal fun ComposerComparisonRow(comparison: ComposerComparison.OtherModel, modifier: Modifier = Modifier) {
    val changeDescription = stringResource(
        if (comparison.lane == ComparisonLane.A) Res.string.cd_change_first_model else Res.string.cd_change_second_model,
        comparison.label,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.compare_also_asking),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.size(8.dp))
        // Takes the free width so a long model id ellipsizes inside the chip.
        Row(modifier = Modifier.weight(1f)) {
            AdaptiveInputChip(
                selected = false,
                onClick = comparison.onChangeModel,
                label = {
                    Text(
                        text = comparison.label,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                avatar = { ComparisonLaneBadge(lane = comparison.lane, size = 20.dp) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.semantics { contentDescription = changeDescription },
            )
        }
    }
}
