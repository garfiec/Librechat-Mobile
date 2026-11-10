package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.delegate.ProviderKeysSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Models & keys: favorites, your provider keys, and how the model picker starts and sorts. */
@Composable
fun ModelsSettingsPage(
    onNavigateToFavorites: () -> Unit,
    onNavigateToProviderKeys: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ChatSettingsViewModel = koinViewModel(),
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()
    var choosingNewChatStart by remember { mutableStateOf(false) }

    // The Provider Keys row's summary loads the first time this page is composed resumed (a
    // recomposed page replays ON_RESUME to its new observer), and re-reads on each resume the entry
    // itself goes through: back from Provider Keys, or from another app.
    OnResume(
        onFirst = accountViewModel::loadProviderKeysSummary,
        onReturn = accountViewModel::refreshProviderKeysSummary,
    )

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "favorites") {
                SettingsRow(
                    icon = Icons.Default.Star,
                    title = stringResource(Res.string.favorites),
                    summary = stringResource(Res.string.favorites_subtitle),
                    onClick = onNavigateToFavorites,
                )
            }
            // Hidden when no reachable endpoint takes a user-provided value: the page would be empty.
            // Shown while the endpoint configs haven't loaded, so the page's load error and Retry stay reachable.
            val providerKeys = account.providerKeysSummary
            if (providerKeys != ProviderKeysSummary.Hidden) {
                row(key = "provider_keys") {
                    SettingsRow(
                        icon = Icons.Default.VpnKey,
                        title = stringResource(Res.string.provider_keys_title),
                        // The general line until every endpoint has answered, then the count.
                        summary = if (providerKeys is ProviderKeysSummary.Count) {
                            providerKeysCountLabel(providerKeys)
                        } else {
                            stringResource(Res.string.provider_keys_subtitle)
                        },
                        onClick = onNavigateToProviderKeys,
                    )
                }
            }
        }
        item(key = "picker_gap") { Spacer(modifier = Modifier.height(24.dp)) }
        adaptiveSection {
            row(key = "new_chat_start") {
                SettingsRow(
                    title = stringResource(Res.string.new_chat_start_title),
                    summary = newChatStartLabel(uiState.preferLastUsedModel),
                    onClick = { choosingNewChatStart = true },
                )
            }
        }
        item(key = "starred_header") {
            AdaptiveSectionHeader(stringResource(Res.string.starred_models_title))
        }
        adaptiveSection {
            row(key = "starred_models") {
                Text(
                    text = stringResource(Res.string.starred_models_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
                StarredModelsChoice(
                    selected = uiState.starredModelsDisplay,
                    onSelect = viewModel::setStarredModelsDisplay,
                )
            }
        }
        item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (choosingNewChatStart) {
        RadioSelectionDialog(
            title = stringResource(Res.string.new_chat_start_title),
            description = stringResource(Res.string.new_chat_start_desc),
            options = listOf(false, true),
            selected = uiState.preferLastUsedModel,
            onSave = {
                viewModel.setPreferLastUsedModel(it)
                choosingNewChatStart = false
            },
            onDismiss = { choosingNewChatStart = false },
            optionLabel = { newChatStartLabel(it) },
        )
    }
}

/** "N of M set": how many of the endpoints that take a user key have one. */
@Composable
internal fun providerKeysCountLabel(count: ProviderKeysSummary.Count): String = pluralStringResource(
    Res.plurals.provider_keys_row_summary,
    count.totalCount,
    count.setCount,
    count.totalCount,
)
