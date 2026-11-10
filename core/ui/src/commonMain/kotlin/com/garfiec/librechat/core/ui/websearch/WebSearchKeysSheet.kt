package com.garfiec.librechat.core.ui.websearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.SecretTextField
import com.garfiec.librechat.core.ui.components.UrlKeyboardOptions
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.web_search_keys_api_key
import com.garfiec.librechat.core.ui.resources.web_search_keys_api_key_optional
import com.garfiec.librechat.core.ui.resources.web_search_keys_body
import com.garfiec.librechat.core.ui.resources.web_search_keys_cancel
import com.garfiec.librechat.core.ui.resources.web_search_keys_keep_hint
import com.garfiec.librechat.core.ui.resources.web_search_keys_none
import com.garfiec.librechat.core.ui.resources.web_search_keys_pinned
import com.garfiec.librechat.core.ui.resources.web_search_keys_remove
import com.garfiec.librechat.core.ui.resources.web_search_keys_remove_failed
import com.garfiec.librechat.core.ui.resources.web_search_keys_save
import com.garfiec.librechat.core.ui.resources.web_search_keys_save_failed
import com.garfiec.librechat.core.ui.resources.web_search_keys_section_providers
import com.garfiec.librechat.core.ui.resources.web_search_keys_section_rerankers
import com.garfiec.librechat.core.ui.resources.web_search_keys_section_scrapers
import com.garfiec.librechat.core.ui.resources.web_search_keys_title
import com.garfiec.librechat.core.ui.resources.web_search_keys_url
import com.garfiec.librechat.core.ui.resources.web_search_keys_url_optional
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The user's own keys for LibreChat search, in web's form: a section per category the server leaves
 * to the user ([WebSearchKeysState.userCategories]), its provider fixed where the admin pinned one.
 * Saved keys are never shown (the server doesn't return them); a field left empty keeps its saved
 * key. [onSave] gets web's install body and closes the sheet on success; a failure keeps the fields
 * and says so. While a save or removal is out, nothing closes the sheet. While a secret is shown the
 * sheet is `secure`; ON_PAUSE re-masks (ON_STOP is too late for the iOS app-switcher snapshot).
 */
@Composable
fun WebSearchKeysSheet(
    keys: WebSearchKeysState,
    onSave: suspend (Map<String, String>) -> Boolean,
    onRemove: suspend () -> Boolean,
    onDismiss: () -> Unit,
) {
    var revealed by remember { mutableStateOf(emptySet<SearchKeyField>()) }
    var busy by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { revealed = emptySet() }
    AdaptiveModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        interceptDismiss = busy,
        modifier = Modifier.clearFocusOnTap(),
        skipPartiallyExpanded = true,
        dragHandle = { LowProfileDragHandle() },
        secure = revealed.isNotEmpty(),
    ) {
        WebSearchKeysSheetContent(
            keys = keys,
            revealed = revealed,
            onRevealedChange = { field, show -> revealed = if (show) revealed + field else revealed - field },
            onBusyChange = { busy = it },
            onSave = onSave,
            onRemove = onRemove,
            onDismiss = onDismiss,
        )
    }
}

/** The sheet's body, apart from the sheet so previews can render it. */
@Composable
internal fun WebSearchKeysSheetContent(
    keys: WebSearchKeysState,
    revealed: Set<SearchKeyField>,
    onRevealedChange: (SearchKeyField, Boolean) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onSave: suspend (Map<String, String>) -> Boolean,
    onRemove: suspend () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = remember(keys) { keys.sheetCategories() }
    val initial = remember(keys) { categories.associateWith(keys::initialProvider) }
    val selected = remember(keys) { mutableStateMapOf<SearchKeyCategory, String>().apply { putAll(initial) } }
    val values = remember { mutableStateMapOf<SearchKeyField, String>() }
    var saving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val scope = rememberCoroutineScope()
    val sectionFields = searchKeySectionFields(categories, selected)
    val busy = saving || removing
    val canSave = if (keys.authenticated) {
        // A newly chosen provider has no saved key to keep, so its required inputs must be filled.
        val switched = categories.filter { selected[it] != initial[it] }
        (values.values.any(String::isNotBlank) || switched.isNotEmpty()) && searchKeysComplete(switched, selected, values)
    } else {
        searchKeysComplete(categories, selected, values)
    }

    fun run(onFlag: (Boolean) -> Unit, failure: StringResource, action: suspend () -> Boolean) {
        scope.launch {
            onFlag(true)
            onBusyChange(true)
            error = null
            val ok = action()
            onFlag(false)
            onBusyChange(false)
            if (ok) onDismiss() else error = failure
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.web_search_keys_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.web_search_keys_body) +
                if (keys.authenticated) " " + stringResource(Res.string.web_search_keys_keep_hint) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        categories.forEach { category ->
            KeySection(
                category = category,
                selected = selected[category],
                pinned = keys.pinned(category) != null,
                fields = sectionFields[category].orEmpty(),
                values = values,
                revealed = revealed,
                onSelect = { selected[category] = it },
                onValueChange = { field, value -> values[field] = value },
                onRevealedChange = onRevealedChange,
            )
        }
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Each button is its own flow item, so on a narrow screen Save drops to a line of its own.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (keys.authenticated) {
                AdaptiveTextButton(
                    onClick = { run({ removing = it }, Res.string.web_search_keys_remove_failed, onRemove) },
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (removing) ButtonProgress()
                    Text(stringResource(Res.string.web_search_keys_remove))
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            AdaptiveOutlinedButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(Res.string.web_search_keys_cancel))
            }
            AdaptiveButton(
                onClick = {
                    val auth = webSearchKeysAuth(keys, selected.toMap(), values.toMap())
                    run({ saving = it }, Res.string.web_search_keys_save_failed) { onSave(auth) }
                },
                enabled = !busy && canSave,
            ) {
                if (saving) ButtonProgress()
                Text(stringResource(Res.string.web_search_keys_save))
            }
        }
    }
}

/** One category: its provider choice (fixed where pinned) and the chosen provider's inputs. */
@Composable
private fun KeySection(
    category: SearchKeyCategory,
    selected: String?,
    pinned: Boolean,
    fields: List<SearchKeyField>,
    values: Map<SearchKeyField, String>,
    revealed: Set<SearchKeyField>,
    onSelect: (String) -> Unit,
    onValueChange: (SearchKeyField, String) -> Unit,
    onRevealedChange: (SearchKeyField, Boolean) -> Unit,
) {
    val none = stringResource(Res.string.web_search_keys_none)
    val labels = category.providers.map { it.displayName.ifEmpty { none } }
    val providerName = category.provider(selected)?.displayName.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(category.titleRes()),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        AdaptivePillChoice(
            options = labels,
            selectedIndex = category.providers.indexOfFirst { it.wire == selected }.coerceAtLeast(0),
            onSelect = { onSelect(category.providers[it].wire) },
            enabled = !pinned,
        )
        if (pinned) {
            Text(
                text = stringResource(Res.string.web_search_keys_pinned),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        fields.forEach { field ->
            val label = stringResource(field.labelRes(), providerName)
            val value = values[field].orEmpty()
            if (field.secret) {
                SecretTextField(
                    value = value,
                    onValueChange = { onValueChange(field, it) },
                    revealed = field in revealed,
                    onRevealedChange = { onRevealedChange(field, it) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AdaptiveOutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(field, it) },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = UrlKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ButtonProgress() {
    AdaptiveCircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
}

private fun SearchKeyCategory.titleRes(): StringResource = when (this) {
    SearchKeyCategory.PROVIDERS -> Res.string.web_search_keys_section_providers
    SearchKeyCategory.SCRAPERS -> Res.string.web_search_keys_section_scrapers
    SearchKeyCategory.RERANKERS -> Res.string.web_search_keys_section_rerankers
}

private fun SearchKeyField.labelRes(): StringResource = when {
    secret && optional -> Res.string.web_search_keys_api_key_optional
    secret -> Res.string.web_search_keys_api_key
    optional -> Res.string.web_search_keys_url_optional
    else -> Res.string.web_search_keys_url
}
