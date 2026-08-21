package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.UiState
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.Space
import kotlin.math.roundToInt

/* ---------------------------------------------------------------- browse */

/**
 * The flat collection with tag narrowing. Tags combine with AND, so the
 * count line always states what the current filter actually selects — with
 * thousands of items, a filter you cannot read back is a filter you cannot
 * trust.
 */
@Composable
fun BrowseScreen(
    state: UiState,
    onOpen: (LinkEntity) -> Unit,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
    onStatus: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                listOf("open" to "Offen", "archived" to "Archiv", "all" to "Alle")
                    .forEach { (key, name) ->
                        FilterChip(
                            selected = state.statusFilter == key,
                            onClick = { onStatus(key) },
                            label = { Text(name) },
                        )
                    }
            }
        }

        if (state.tagCounts.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.tagCounts.entries.toList(), key = { it.key }) { (tag, n) ->
                        FilterChip(
                            selected = tag in state.activeTags,
                            onClick = { onToggleTag(tag) },
                            label = { Text("${TOPIC_NAMES[tag] ?: tag} $n") },
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.activeTags.isEmpty()) {
                        "${state.links.size} Links"
                    } else {
                        "${state.links.size} Links · " +
                            state.activeTags.joinToString(" + ") { TOPIC_NAMES[it] ?: it }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.activeTags.isNotEmpty()) {
                    Text(
                        text = "Filter zurücksetzen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onClearTags)
                            .padding(4.dp),
                    )
                }
            }
        }

        if (state.links.isEmpty()) {
            item { EmptyState("Nichts gefunden. Teile einen Link aus einer anderen App hierher.") }
        }
        items(state.links, key = { it.id }) { link ->
            LinkCard(link = link, onClick = { onOpen(link) }, onTagClick = onToggleTag)
        }
    }
}

/* ---------------------------------------------------------------- spaces */

/**
 * The start screen, shaped like a feed rather than a menu.
 *
 * The space row behaves the way YouTube's category chips do: it sticks to the
 * top, slides away as you scroll into the content, and comes straight back on
 * the first upward scroll. Tapping a space *filters the feed below* instead of
 * navigating somewhere else — the old screen had a card and an "Alle ansehen"
 * link that led to the identical view, and left the lower two thirds empty.
 */
@Composable
fun SpacesScreen(
    state: UiState,
    onOpenSpace: (Space?) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onToggleTag: (String) -> Unit,
    onAttention: (String) -> Unit,
    onTogglePin: (Space) -> Unit,
    onArchive: (LinkEntity) -> Unit = {},
) {
    val density = LocalDensity.current
    var columns by remember { mutableStateOf(2) }
    var headerHeight by remember { mutableStateOf(0f) }
    var headerOffset by remember { mutableStateOf(0f) }

    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // consume nothing: only translate the header, never the list
                headerOffset = (headerOffset + available.y).coerceIn(-headerHeight, 0f)
                return Offset.Zero
            }
        }
    }

    val selected = state.openSpace
    val feed = if (selected == null) state.links else state.spaceLinks

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(collapse),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoomColumns(columns) { columns = it },
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = with(density) { headerHeight.toDp() } + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AttentionTile(
                        label = "Posteingang",
                        hint = "Neu oder nicht angereichert",
                        count = state.inboxCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onAttention("inbox") },
                    )
                    AttentionTile(
                        label = "Ohne Tag",
                        hint = "Noch keiner Gruppe zugeordnet",
                        count = state.untaggedCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onAttention("untagged") },
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selected?.let { "${it.icon}  ${it.name}" }
                                ?: "Zuletzt gespeichert",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${feed.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ZoomHint(columns)
                }
            }

            if (selected != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (selected.pinned) "📌 Angeheftet" else "Anheften",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onTogglePin(selected) },
                        )
                        Text(
                            text = "Filter aufheben",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenSpace(null) },
                        )
                    }
                }
            }

            if (feed.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        if (selected == null) {
                            "Noch nichts gespeichert. Teile einen Link aus einer anderen App hierher."
                        } else {
                            "Noch nichts in diesem Space. Sobald Links passend getaggt sind, erscheinen sie hier."
                        },
                    )
                }
            }
            items(feed, key = { it.id }) { link ->
                TabCard(
                    link = link,
                    onClick = { onOpenLink(link) },
                    onDismiss = { onArchive(link) },
                    compact = columns >= 3,
                )
            }
        }

        // sticky, self-measuring header — offset is driven by the scroll above
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x = 0, y = headerOffset.roundToInt()) }
                .onSizeChanged { headerHeight = it.height.toFloat() },
        ) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    item {
                        SpaceChip(
                            icon = "🗂",
                            name = "Alle",
                            count = state.links.size,
                            selected = selected == null,
                            onClick = { onOpenSpace(null) },
                        )
                    }
                    items(state.spaces, key = { it.id }) { space ->
                        SpaceChip(
                            icon = space.icon,
                            name = space.name,
                            count = state.spaceCounts[space.id] ?: 0,
                            selected = selected?.id == space.id,
                            onClick = { onOpenSpace(space) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceChip(
    icon: String,
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(icon, style = MaterialTheme.typography.titleSmall)
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AttentionTile(
    label: String,
    hint: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text(
                "$count",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/* ---------------------------------------------------------------- search */

@Composable
fun SearchScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onToggleTag: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            placeholder = { Text("Titel, URL oder Tag suchen …") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.query.isBlank()) {
                item {
                    Text(
                        "Häufige Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.tagCounts.entries.take(12).toList(), key = { it.key }) { (tag, n) ->
                            FilterChip(
                                selected = tag in state.activeTags,
                                onClick = { onToggleTag(tag) },
                                label = { Text("${TOPIC_NAMES[tag] ?: tag} $n") },
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "${state.links.size} Treffer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.links, key = { it.id }) { link ->
                LinkCard(link = link, onClick = { onOpen(link) }, onTagClick = onToggleTag)
            }
        }
    }
}

/* ----------------------------------------------------------------- inbox */

@Composable
fun InboxScreen(
    state: UiState,
    onMode: (String) -> Unit,
    onOpen: (LinkEntity) -> Unit,
    onToggleTag: (String) -> Unit,
    onStartSession: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SessionStarter(
                pending = state.untaggedCount + state.inboxCount,
                curatedTotal = state.curatedTotal,
                onStart = onStartSession,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilterChip(
                    selected = state.attentionMode == "inbox",
                    onClick = { onMode("inbox") },
                    label = { Text("Posteingang ${state.inboxCount}") },
                )
                FilterChip(
                    selected = state.attentionMode == "untagged",
                    onClick = { onMode("untagged") },
                    label = { Text("Ohne Tag ${state.untaggedCount}") },
                )
            }
        }
        item {
            Text(
                text = if (state.attentionMode == "inbox") {
                    "Diese Links warten noch auf die Anreicherung oder konnten nicht geladen werden."
                } else {
                    "Diese Links haben noch keinen Tag — tippe einen an, um ihn zu vergeben."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.attentionLinks.isEmpty()) {
            item { EmptyState("Nichts zu tun. Genau so soll es sein.") }
        }
        items(state.attentionLinks, key = { it.id }) { link ->
            LinkCard(link = link, onClick = { onOpen(link) }, onTagClick = onToggleTag)
        }
    }
}

/** Small square icon slot used by the navigation bar. */
@Composable
fun NavIcon(emoji: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, style = MaterialTheme.typography.titleSmall)
        }
    }
}
