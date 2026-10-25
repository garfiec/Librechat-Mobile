package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.citation_sources_title
import com.garfiec.librechat.feature.chat.resources.file_search_pages
import com.garfiec.librechat.feature.chat.resources.files_more
import com.garfiec.librechat.feature.chat.resources.sources_agent_file
import com.garfiec.librechat.feature.chat.resources.sources_agent_files
import com.garfiec.librechat.feature.chat.resources.sources_more
import com.garfiec.librechat.feature.chat.resources.sources_tab_all
import com.garfiec.librechat.feature.chat.resources.sources_tab_files
import com.garfiec.librechat.feature.chat.resources.sources_tab_images
import com.garfiec.librechat.feature.chat.resources.sources_tab_news
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** One page or image a reply's searches found, for the Sources panel. */
@Immutable
internal data class PanelSource(
    val link: String,
    val title: String?,
    val snippet: String?,
    val imageUrl: String?,
)

/** Every source a reply's searches found, by tab (upstream `Sources.tsx`, across all turns). */
@Immutable
internal data class SourcesPanelData(
    val organic: List<PanelSource>,
    val news: List<PanelSource>,
    val images: List<PanelSource>,
    val files: List<FileSearchCitation>,
) {
    val isEmpty: Boolean get() = organic.isEmpty() && news.isEmpty() && images.isEmpty() && files.isEmpty()
}

/**
 * Upstream's merge: organic results and non-file references by link (the last copy wins), top
 * stories as news, image references and image results as images, and file-search sources one per
 * file with their pages joined and the best relevance kept.
 */
internal fun collectSourcesPanel(attachments: List<Attachment>): SourcesPanelData {
    val organic = LinkedHashMap<String, PanelSource>()
    val news = LinkedHashMap<String, PanelSource>()
    val images = LinkedHashMap<String, PanelSource>()
    webSearchData(attachments, toolCallId = null).forEach { data ->
        data.organic.orEmpty().forEach { s ->
            safeWebUrl(s.link)?.let { organic[it] = PanelSource(it, s.title, s.snippet, s.imageUrl) }
        }
        data.references.orEmpty().forEach { s ->
            val link = safeWebUrl(s.link) ?: return@forEach
            val source = PanelSource(link, s.title ?: s.attribution, s.snippet, s.imageUrl)
            if (s.type == "image") images[link] = source else if (s.type != "file") organic[link] = source
        }
        data.topStories.orEmpty().forEach { s ->
            safeWebUrl(s.link)?.let { news[it] = PanelSource(it, s.title, s.snippet, s.imageUrl) }
        }
        data.images.orEmpty().forEach { s ->
            val picture = safeWebUrl(s.imageUrl) ?: return@forEach
            images[picture] = PanelSource(picture, s.title, null, safeWebUrl(s.thumbnailUrl) ?: picture)
        }
    }
    val files = collectFileSearchSources(attachments.filter { it.type == ToolConstants.FILE_SEARCH }, toolCallId = null)
        .map { it.copy(pages = it.pages.sorted()) }
    return SourcesPanelData(organic.values.toList(), news.values.toList(), images.values.toList(), files)
}

private enum class SourcesTab { ALL, NEWS, IMAGES, FILES }

private const val VISIBLE_SOURCE_CARDS = 3
private const val VISIBLE_FILE_CARDS_ALL = 4

/**
 * A reply's sources in one place, as the web shows them for multi-agent replies: tabs for all
 * pages, news, images and the agent's files. A tab shows its first cards and a "+N" tile that
 * opens the full list. Upstream draws this only for parallel content; [MessageContentAndActions]
 * applies the same gate, per Compare pane.
 */
@Composable
internal fun SourcesPanel(attachments: List<Attachment>, stateKey: String, modifier: Modifier = Modifier) {
    val data = remember(attachments) { collectSourcesPanel(attachments) }
    if (data.isEmpty) return
    val tabs = buildList {
        if (data.organic.isNotEmpty() || data.news.isNotEmpty()) add(SourcesTab.ALL)
        if (data.news.isNotEmpty()) add(SourcesTab.NEWS)
        if (data.images.isNotEmpty()) add(SourcesTab.IMAGES)
        if (data.files.isNotEmpty()) add(SourcesTab.FILES)
    }
    var selected by rememberSaveable(key = "sources-panel:$stateKey") { mutableIntStateOf(0) }
    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    val titles = tabs.map {
        stringResource(
            when (it) {
                SourcesTab.ALL -> Res.string.sources_tab_all
                SourcesTab.NEWS -> Res.string.sources_tab_news
                SourcesTab.IMAGES -> Res.string.sources_tab_images
                SourcesTab.FILES -> Res.string.sources_tab_files
            },
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tabs.size > 1) {
            AdaptivePillChoice(options = titles, selectedIndex = tabs.indexOf(tab), onSelect = { selected = it })
        }
        when (tab) {
            SourcesTab.ALL -> SourceCards(data.organic + data.news)
            SourcesTab.NEWS -> SourceCards(data.news)
            SourcesTab.IMAGES -> ImageGrid(data.images, stateKey)
            SourcesTab.FILES -> FileCards(data.files)
        }
    }
}

@Composable
private fun SourceCards(sources: List<PanelSource>) {
    var showAll by remember { mutableStateOf(false) }
    val visible = if (sources.size > VISIBLE_SOURCE_CARDS + 1) sources.take(VISIBLE_SOURCE_CARDS) else sources
    val rest = sources.drop(visible.size)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { SourceCard(it, Modifier.weight(1f)) }
        if (rest.isNotEmpty()) {
            MoreTile(
                label = pluralStringResource(Res.plurals.sources_more, rest.size, rest.size),
                hosts = rest.mapNotNull { hostOf(it.link) }.distinct().take(MAX_STACKED_FAVICONS),
                modifier = Modifier.weight(1f),
                onClick = { showAll = true },
            )
        }
    }
    if (showAll) {
        SourcesListSheet(title = stringResource(Res.string.citation_sources_title), onDismiss = { showAll = false }) {
            sources.forEachIndexed { i, source ->
                if (i > 0) AdaptiveDivider(modifier = Modifier.padding(horizontal = 24.dp))
                SourceListRow(source)
            }
        }
    }
}

@Composable
private fun SourceCard(source: PanelSource, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val domain = hostOf(source.link) ?: source.link
    Column(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button) { runCatching { uriHandler.openUri(source.link) } }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Favicon(model = faviconUrl(domain), size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = domain,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = source.title?.takeIf { it.isNotBlank() } ?: domain,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreTile(label: String, hosts: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hosts.isNotEmpty()) SourceFaviconStack(hosts)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceListRow(source: PanelSource) {
    val uriHandler = LocalUriHandler.current
    val domain = hostOf(source.link) ?: source.link
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { runCatching { uriHandler.openUri(source.link) } }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Favicon(model = faviconUrl(domain), size = 20.dp, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title?.takeIf { it.isNotBlank() } ?: domain,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            plainSnippet(source.snippet)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = domain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Image results, loading only after a tap: they come from third-party hosts (see `SearchVerticalsSection`). */
@Composable
private fun ImageGrid(images: List<PanelSource>, stateKey: String) {
    val uriHandler = LocalUriHandler.current
    var load by rememberSaveable(key = "sources-panel-images:$stateKey") { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!load) LoadImagesChip(onClick = { load = true })
        images.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { image ->
                    val name = image.title.orEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(role = Role.Button) { runCatching { uriHandler.openUri(image.link) } }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (load) {
                            AsyncImage(
                                model = image.imageUrl ?: image.link,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (name.isNotBlank()) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                                    .padding(4.dp),
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The agent's files: all of them up to four, else three and a "+N files" tile (upstream `FilesGroup`). */
@Composable
private fun FileCards(files: List<FileSearchCitation>) {
    var showAll by remember { mutableStateOf(false) }
    val visible = if (files.size <= VISIBLE_FILE_CARDS_ALL) files else files.take(VISIBLE_SOURCE_CARDS)
    val rest = files.drop(visible.size)
    val fileActions = rememberToolFileActions(LocalMarkdownMedia.current.baseUrl)
    val open = { file: FileSearchCitation -> file.asAttachment()?.let(fileActions::openToolFile) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { FileCard(it, onClick = { open(it) }, modifier = Modifier.weight(1f)) }
                if (row.size == 1 && rest.isEmpty()) Spacer(Modifier.weight(1f))
                if (row.size == 1 && rest.isNotEmpty()) {
                    MoreTile(
                        label = pluralStringResource(Res.plurals.files_more, rest.size, rest.size),
                        hosts = emptyList(),
                        modifier = Modifier.weight(1f),
                        onClick = { showAll = true },
                    )
                }
            }
        }
    }
    if (showAll) {
        SourcesListSheet(title = stringResource(Res.string.sources_agent_files), onDismiss = { showAll = false }) {
            files.forEach { file ->
                FileCard(
                    file,
                    onClick = {
                        showAll = false
                        open(file)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FileCard(file: FileSearchCitation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val kind = remember(file.fileName, file.fileType) { fileKindOf(file.fileName, file.fileType) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (file.openable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeTile(kind = kind, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.sources_agent_file),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (file.pages.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.file_search_pages, file.pages.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcesListSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            content()
        }
    }
}
