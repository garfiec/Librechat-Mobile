package com.garfiec.librechat.feature.agents.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.agents.AgentDetailDisplayData
import com.garfiec.librechat.feature.agents.components.agentCategoryLabel
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.util.htmlDescriptionToAnnotatedString
import com.garfiec.librechat.feature.agents.util.rememberClipboardWriter
import com.garfiec.librechat.feature.agents.viewmodel.AgentActionError
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailEvent
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Wide screens keep the page a readable column rather than stretching it edge to edge. */
private val PageMaxWidth = 600.dp
private val LabelShape = RoundedCornerShape(8.dp)
private val StarterShape = RoundedCornerShape(16.dp)

@Composable
fun AgentDetailScreen(
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    onStartChatWithPrompt: (agentId: String, prompt: String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    modifier: Modifier = Modifier,
    agentId: String? = null,
    viewModel: AgentDetailViewModel = koinViewModel { parametersOf(agentId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnDuplicated by rememberUpdatedState(onDuplicate)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AgentDetailEvent.Deleted -> currentOnBack()
                is AgentDetailEvent.Duplicated -> currentOnDuplicated(event.agentId)
            }
        }
    }

    val clipboard = rememberClipboardWriter()
    val linkLabel = stringResource(Res.string.agent_copy_link)
    AgentDetailContent(
        uiState = uiState,
        actions = AgentDetailActions(
            onBack = onBack,
            onStartChat = { id -> onStartChat(id) },
            onStartChatWithPrompt = { id, prompt -> onStartChatWithPrompt(id, prompt) },
            onEdit = onEdit,
            onCopyLink = { clipboard.copy(viewModel.chatLink(), linkLabel) },
            onTogglePin = viewModel::togglePin,
            onDuplicate = viewModel::duplicateAgent,
            onDelete = viewModel::showDeleteConfirmation,
            onConfirmDelete = viewModel::deleteAgent,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onRetry = viewModel::loadAgent,
            onDismissActionError = viewModel::dismissActionError,
        ),
        modifier = modifier,
    )
}

/** What [AgentDetailContent] can ask of its owner. */
internal class AgentDetailActions(
    val onBack: () -> Unit = {},
    val onStartChat: (agentId: String) -> Unit = {},
    val onStartChatWithPrompt: (agentId: String, prompt: String) -> Unit = { _, _ -> },
    val onEdit: (agentId: String) -> Unit = {},
    val onCopyLink: () -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissActionError: () -> Unit = {},
)

@Composable
internal fun AgentDetailContent(
    uiState: AgentDetailUiState,
    actions: AgentDetailActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentActions by rememberUpdatedState(actions)
    val linkCopied = stringResource(Res.string.agent_link_copied)

    // A failed action is a snackbar over the page it was tried on; the page itself stays.
    val actionError = uiState.actionError
    val actionErrorText = actionError?.let { actionErrorText(it) }
    LaunchedEffect(actionError) {
        if (actionError == null || actionErrorText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(actionErrorText)
        currentActions.onDismissActionError()
    }

    if (uiState.showDeleteDialog) {
        DeleteAgentDialog(name = uiState.agent?.name, actions = actions)
    }

    val agent = uiState.agent
    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), actions.onBack),
                    title = BarTitle(
                        agent?.let { it.name ?: stringResource(Res.string.agent_unnamed) }
                            ?: stringResource(Res.string.agent_title),
                    ),
                    actions = if (agent != null) {
                        agentBarActions(
                            uiState = uiState,
                            agentId = agent.id,
                            actions = actions,
                            onCopyLink = {
                                actions.onCopyLink()
                                scope.launch { snackbarHostState.showSnackbar(linkCopied) }
                            },
                        )
                    } else {
                        emptyList()
                    },
                ),
                materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        val loadError = uiState.loadError
        when {
            agent != null -> AgentPage(
                agent = agent,
                uiState = uiState,
                actions = actions,
                modifier = Modifier.padding(innerPadding),
            )

            loadError != null && !uiState.isLoading -> ErrorBanner(
                message = loadError.message ?: stringResource(Res.string.agent_load_failed),
                modifier = Modifier.padding(innerPadding),
                onRetry = actions.onRetry,
            )

            else -> LoadingIndicator(modifier = Modifier.padding(innerPadding))
        }
    }
}

/**
 * Pin, then ⋮ with every action on the agent. While a Duplicate or Delete is in flight the ⋮ is a
 * spinner, so the menu can't start a second one and the wait is visible.
 */
@Composable
private fun agentBarActions(
    uiState: AgentDetailUiState,
    agentId: String,
    actions: AgentDetailActions,
    onCopyLink: () -> Unit,
): List<BarAction> {
    val pin = BarAction.Toggle(
        id = "pin",
        iconOff = BarIcons.Star,
        iconOn = BarIcons.StarFilled,
        label = stringResource(if (uiState.isPinned) Res.string.agent_unpin else Res.string.agent_pin),
        checked = uiState.isPinned,
        onCheckedChange = { actions.onTogglePin() },
    )
    val moreLabel = stringResource(Res.string.cd_more_actions)
    if (uiState.isDuplicating || uiState.isDeleting) {
        return listOf(pin, BarAction.Icon(id = "more", icon = BarIcons.More, label = moreLabel, busy = true, onClick = {}))
    }
    val copyLink = stringResource(Res.string.agent_copy_link)
    val edit = stringResource(Res.string.edit)
    val duplicate = stringResource(Res.string.duplicate)
    val delete = stringResource(Res.string.delete)
    val items = buildList {
        add(BarMenuItem(id = "copy_link", label = copyLink, icon = BarIcons.Link, onClick = onCopyLink))
        if (uiState.canEdit) {
            add(BarMenuItem(id = "edit", label = edit, icon = BarIcons.EditFilled, onClick = { actions.onEdit(agentId) }))
        }
        if (uiState.canDuplicate) {
            add(BarMenuItem(id = "duplicate", label = duplicate, icon = BarIcons.DuplicateFilled, onClick = actions.onDuplicate))
        }
        if (uiState.canDelete) {
            add(
                BarMenuItem(
                    id = "delete",
                    label = delete,
                    icon = BarIcons.DeleteFilled,
                    destructive = true,
                    onClick = actions.onDelete,
                ),
            )
        }
    }
    return listOf(pin, BarAction.Menu(id = "more", icon = BarIcons.More, label = moreLabel, sections = listOf(BarMenuSection(items))))
}

@Composable
private fun actionErrorText(error: AgentActionError): String = when (error) {
    is AgentActionError.Delete -> error.message ?: stringResource(Res.string.agent_delete_failed)
    is AgentActionError.Duplicate -> error.message ?: stringResource(Res.string.agent_duplicate_failed)
    is AgentActionError.Pin -> error.message ?: stringResource(Res.string.agent_pin_failed)
    is AgentActionError.PinLimit -> pluralStringResource(Res.plurals.agent_pin_limit, error.max, error.max)
}

@Composable
private fun DeleteAgentDialog(name: String?, actions: AgentDetailActions) {
    AdaptiveAlertDialog(
        onDismissRequest = actions.onDismissDelete,
        title = { Text(stringResource(Res.string.delete_agent)) },
        text = {
            Text(stringResource(Res.string.delete_agent_confirm, name ?: stringResource(Res.string.delete_this_agent)))
        },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDelete) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The page: who the agent is, Start chat, what it does, and what to ask it. Model shows for every
 * viewer (the VIEW read carries it); tools only from the full (`/expanded`) record, since nothing
 * else carries the agent's real ones.
 */
@Composable
internal fun AgentPage(
    agent: AgentDetailDisplayData,
    uiState: AgentDetailUiState,
    actions: AgentDetailActions,
    modifier: Modifier = Modifier,
) {
    val categoriesByValue = remember(uiState.categories) { uiState.categories.associateBy { it.value } }
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PageMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AgentHeader(agent = agent, categoryLabel = agentCategoryLabel(agent.categoryValue, categoriesByValue))

            AdaptiveButton(onClick = { actions.onStartChat(agent.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(Res.string.start_chat))
            }

            AboutSection(agent)

            val model = agent.model
            if (model != null) {
                LabelledSection(label = stringResource(Res.string.label_model)) {
                    Text(text = model, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (uiState.isFullRecord && agent.tools.isNotEmpty()) {
                LabelledSection(label = stringResource(Res.string.label_tools)) { ToolLabels(agent.tools) }
            }

            if (agent.conversationStarters.isNotEmpty()) {
                StartersSection(
                    starters = agent.conversationStarters,
                    onStarter = { prompt -> actions.onStartChatWithPrompt(agent.id, prompt) },
                )
            }
        }
    }
}

/** Tile, name, who to contact, and the Top pick and category marks. The "by <author>" byline is gone: contact replaces it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentHeader(agent: AgentDetailDisplayData, categoryLabel: String?) {
    val name = agent.name ?: stringResource(Res.string.agent_unnamed)
    Row(verticalAlignment = Alignment.Top) {
        InitialTile(seed = agent.id, label = name, imageUrl = agent.avatarUrl, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            agent.contact?.let { ContactLine(name = it.name, email = it.email) }
            if (agent.isPromoted || categoryLabel != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val style = MaterialTheme.typography.labelMedium
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    if (agent.isPromoted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.agents_top_pick), style = style, color = color)
                        }
                    }
                    if (categoryLabel != null) Text(categoryLabel, style = style, color = color)
                }
            }
        }
    }
}

/** "Contact: <name>". With an email it is a button that starts one. */
@Composable
private fun ContactLine(name: String?, email: String?) {
    val text = stringResource(Res.string.agent_contact_line, name ?: email.orEmpty())
    val style = MaterialTheme.typography.bodyMedium
    if (email == null) {
        Text(text = text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val uriHandler = LocalUriHandler.current
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(LabelShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.agent_contact_send_email)) {
                // No mail app to take it: nothing to do, and nothing worth crashing over.
                runCatching { uriHandler.openUri("mailto:$email") }
            }
            .padding(vertical = 4.dp),
    )
}

/** The description, then one quiet line: when it last changed, and whether it is public. */
@Composable
private fun AboutSection(agent: AgentDetailDisplayData) {
    val description = agent.description
    val public = if (agent.isPublic) stringResource(Res.string.agent_public) else null
    val facts = listOfNotNull(updatedText(agent.updatedAt), public)
    if (description == null && facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (description != null) {
            val linkColor = MaterialTheme.colorScheme.primary
            val rendered = remember(description, linkColor, agent.linkBaseUrl) {
                htmlDescriptionToAnnotatedString(
                    description,
                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    baseUrl = agent.linkBaseUrl,
                )
            }
            Text(text = rendered, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (facts.isNotEmpty()) {
            Text(
                text = facts.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Updated <when>", in the user's date format; null when the server sent no time it can read. */
@Composable
private fun updatedText(iso: String?): String? {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val label = remember(iso, format, reference) { iso?.let(::parseIsoInstantOrNull)?.listLabel(format, reference) }
    return label?.let { stringResource(Res.string.agent_updated, it.resolve()) }
}

@Composable
private fun LabelledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** What the agent can use, as labels: there is nothing to do with a tool from here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolLabels(tools: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEach { tool ->
            Text(
                text = tool,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(LabelShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** "Try asking": each conversation starter is a card that opens a new chat with it. */
@Composable
private fun StartersSection(starters: List<String>, onStarter: (String) -> Unit) {
    LabelledSection(label = stringResource(Res.string.agent_try_asking)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            starters.forEach { starter ->
                AdaptiveCard(
                    onClick = { onStarter(starter) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = StarterShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Text(
                        text = starter,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
