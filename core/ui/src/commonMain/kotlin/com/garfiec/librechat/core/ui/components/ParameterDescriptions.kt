package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ParameterDefinition
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.param_thinking_between_tools_description
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The string resource for a registry description that has been translated, or null when the
 * description is only available in English.
 *
 * Keyed on the English text because [ParameterDefinition] lives in `:core:model`, which cannot
 * hold a compose resource. Every control that renders a parameter description goes through
 * [localizedDescription], so a translated description only needs an entry here.
 */
internal fun ParameterDefinition.descriptionRes(): StringResource? = when (description) {
    EndpointParameterRegistry.BETWEEN_TOOLS_THINKING_DESCRIPTION -> Res.string.param_thinking_between_tools_description
    else -> null
}

/** The description to show under a parameter control, translated when a translation exists. */
@Composable
internal fun ParameterDefinition.localizedDescription(): String? =
    descriptionRes()?.let { stringResource(it) } ?: description
