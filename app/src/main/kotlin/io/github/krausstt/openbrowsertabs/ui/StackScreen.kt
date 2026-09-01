package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.UiState
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.data.LinkEntity

/**
 * The inbox, rebuilt around a stack instead of a backlog.
 *
 * The old screen led with two honest numbers — 791 waiting, 454 untagged —
 * and those two numbers were the entire problem. A count that large is not
 * information, it is a verdict, and the only available response to a verdict
 * is to close the app. Worse, it grows on its own: every share makes it
 * bigger, so the interface punished exactly the behaviour the app depends on.
 *
 * So the pile is gone from the top of the screen. What is here instead is a
 * stack of seven, chosen by [io.github.krausstt.openbrowsertabs.core.InboxBatch]
 * for where one tap still buys something, and each card carries the same
 * five reactions as the save sheet — so clearing one is a tap, not a visit
 * to a detail screen. Seven is finishable. When it is finished the screen
 * says so, and the next seven are one tap away *if you want them*.
 *
 * The remaining total is still reachable, one line down, in grey. It is a
 * fact about the collection, not a demand.
 */
@Composable
fun StackScreen(
    state: UiState,
    onOpen: (LinkEntity) -> Unit,
    onReact: (Long, String) -> Unit,
    onArchive: (Long) -> Unit,
    onNextStack: () -> Unit,
    onStartSession: (Int) -> Unit,
    onShowRest: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StackHeader(state, onNextStack) }

        if (state.stack.isEmpty()) {
            item {
                EmptyState(
                    "Stapel leer. Der Rest läuft dir nicht weg — " +
                        "er wartet ohne dass er größer wird.",
                )
            }
        }

        items(state.stack, key = { it.id }) { link ->
            StackCard(
                link = link,
                onOpen = { onOpen(link) },
                onReact = { onReact(link.id, it) },
                onArchive = { onArchive(link.id) },
            )
        }

        item { RestLine(state, onShowRest, onStartSession) }

        if (state.showingRest) {
            items(state.attentionLinks, key = { "rest-${it.id}" }) { link ->
                LinkCard(link = link, onClick = { onOpen(link) }, onTagClick = {})
            }
        }
    }
}

@Composable
private fun StackHeader(state: UiState, onNextStack: () -> Unit) {
    val total = state.stackSize
    val left = state.stack.size
    val done = (total - left).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Dein Stapel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (left > 0) {
                    "$left Stück. Ein Tipp pro Karte genügt — du sagst nur, warum du es aufgehoben hast."
                } else {
                    "Fertig für jetzt."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.contextToday > 0) {
                Text(
                    "Heute eingeordnet: ${state.contextToday}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (left == 0) {
                TextButton(onClick = onNextStack, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Noch sieben")
                }
            }
        }
    }
}

/**
 * One entry with its reactions inline.
 *
 * The card shows what the page is *about* only as far as it honestly can —
 * headline, host, and whatever the enrichment already found. It never shows
 * a tag suggestion here: guessing wrong costs a correction, and correcting
 * is exactly the work this screen exists to avoid.
 */
@Composable
private fun StackCard(
    link: LinkEntity,
    onOpen: () -> Unit,
    onReact: (String) -> Unit,
    onArchive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(onClick = onOpen),
            ) {
                MonogramTile(link.host, size = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        Headline.best(link.title, link.canonicalUrl, link.siteName),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(link.host)
                            if (link.nSightings > 1) append(" · ${link.nSightings}× gesehen")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onArchive)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            ReactionRow(selected = link.reaction, onReact = onReact)
        }
    }
}

/** The honest total, deliberately below the fold and deliberately quiet. */
@Composable
private fun RestLine(state: UiState, onShowRest: () -> Unit, onStartSession: (Int) -> Unit) {
    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Ohne Kontext insgesamt: ${state.withoutContextCount} · " +
                "kuratiert: ${state.curatedTotal}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onShowRest) {
                Text(if (state.showingRest) "Liste ausblenden" else "Ganze Liste zeigen")
            }
            TextButton(onClick = { onStartSession(10) }) { Text("Aufräum-Session") }
        }
    }
}
