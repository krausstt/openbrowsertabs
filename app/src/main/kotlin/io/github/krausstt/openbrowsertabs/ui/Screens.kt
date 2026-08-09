package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.UiState
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.Space

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

@Composable
fun SpacesScreen(
    state: UiState,
    onOpenSpace: (Space?) -> Unit,
    onOpenLink: (LinkEntity) -> Unit,
    onToggleTag: (String) -> Unit,
    onAttention: (String) -> Unit,
    onTogglePin: (Space) -> Unit,
) {
    val open = state.openSpace
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (open == null) {
            item { SectionHeader("Spaces", modifier = Modifier.padding(top = 6.dp)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.spaces, key = { it.id }) { space ->
                        SpaceCard(
                            space = space,
                            count = state.spaceCounts[space.id] ?: 0,
                            onClick = { onOpenSpace(space) },
                        )
                    }
                }
            }

            item { SectionHeader("Braucht Aufmerksamkeit") }
            item {
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

            val pinned = state.spaces.firstOrNull { it.pinned }
            if (pinned != null) {
                item {
                    SectionHeader(
                        "${pinned.icon} ${pinned.name}",
                        trailing = "Alle ansehen",
                        onTrailingClick = { onOpenSpace(pinned) },
                    )
                }
                val preview = state.spaceCounts[pinned.id] ?: 0
                item {
                    Text(
                        "$preview Einträge · Tags: " +
                            (pinned.matchTags.map { TOPIC_NAMES[it] ?: it } +
                                pinned.matchCategories.map { CATEGORY_NAMES[it] ?: it })
                                .joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Spaces sind gespeicherte Filter — Einträge wandern automatisch hinein, " +
                        "sobald sie passend getaggt sind.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${open.icon}  ${open.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (open.pinned) "📌 Angeheftet" else "Anheften",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onTogglePin(open) }
                            .padding(4.dp),
                    )
                }
            }
            item {
                Text(
                    "← Alle Spaces",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenSpace(null) }
                        .padding(vertical = 4.dp),
                )
            }
            item {
                Text(
                    "${state.spaceLinks.size} Einträge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.spaceLinks.isEmpty()) {
                item { EmptyState("Noch nichts in diesem Space. Sobald Links passend getaggt sind, erscheinen sie hier.") }
            }
            items(state.spaceLinks, key = { it.id }) { link ->
                LinkCard(link = link, onClick = { onOpenLink(link) }, onTagClick = onToggleTag)
            }
        }
    }
}

@Composable
private fun SpaceCard(space: Space, count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (space.pinned) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(space.icon, style = MaterialTheme.typography.headlineSmall)
            Text(
                space.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "$count Einträge",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
