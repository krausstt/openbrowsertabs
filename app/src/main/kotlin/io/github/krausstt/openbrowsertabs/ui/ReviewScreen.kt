package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.ReviewSession
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.core.Headline

/**
 * One entry at a time instead of a list of 1,300.
 *
 * Deliberately *not* streak-based: the research on ADHD information
 * behaviour is blunt that daily-streak mechanics turn into another thing to
 * fail at. The session is short, finite and self-chosen, and the reward is
 * the real one — how many entries this one just connected to. Nothing is
 * lost by stopping, and there is no counter that grows while you sleep.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    session: ReviewSession,
    curatedTotal: Int,
    onCommit: (List<String>, String?) -> Unit,
    onSkip: () -> Unit,
    onArchive: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onEnd: () -> Unit,
) {
    val link = session.current
    if (link == null || session.finished) {
        SessionSummary(session, curatedTotal, onEnd)
        return
    }

    var picked by remember(link.id) { mutableStateOf(setOf<String>()) }
    var custom by remember(link.id) { mutableStateOf("") }
    var summary by remember(link.id) { mutableStateOf("") }
    val progress by animateFloatAsState(session.progress, label = "progress")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${session.done} von ${session.target}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onEnd) { Text("Beenden") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )

        // the reward from the previous card, still visible while you work
        AnimatedVisibility(
            visible = session.lastReward != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                session.lastReward.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonogramTile(link.host, size = 44)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            link.label ?: link.title?.let {
                                Headline.shortHeadline(it, link.canonicalUrl)
                            } ?: link.host,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${CATEGORY_NAMES[link.category] ?: link.category} · ${link.host}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                link.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                TextButton(
                    onClick = { onOpenUrl(link.canonicalUrl) },
                    modifier = Modifier.padding(top = 2.dp),
                ) { Text("Seite öffnen ↗") }
            }
        }

        Text(
            "Passende Tags antippen",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        // suggestions come from this entry's neighbours, so the right answer
        // is usually already on screen — one tap instead of typing
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            session.suggestions.forEach { tag ->
                FilterChip(
                    selected = tag in picked,
                    onClick = {
                        picked = if (tag in picked) picked - tag else picked + tag
                    },
                    label = { Text(TOPIC_NAMES[tag] ?: tag) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.fillMaxWidth(0.68f),
                placeholder = { Text("eigener Tag") },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    val clean = custom.trim().removePrefix("#").lowercase().replace(' ', '_')
                    if (clean.isNotEmpty()) picked = picked + clean
                    custom = ""
                },
                enabled = custom.isNotBlank(),
            ) { Text("+") }
        }

        OutlinedTextField(
            value = summary,
            onValueChange = { summary = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Zusammenfassung (optional)") },
            minLines = 2,
            maxLines = 6,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Button(
                onClick = { onCommit(picked.toList(), summary.takeIf { it.isNotBlank() }) },
                enabled = picked.isNotEmpty() || summary.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Sichern & weiter") }
            OutlinedButton(onClick = onSkip) { Text("Später") }
            OutlinedButton(onClick = onArchive) { Text("🗄") }
        }
    }
}

@Composable
private fun SessionSummary(session: ReviewSession, curatedTotal: Int, onEnd: () -> Unit) {
    // milestones are cumulative and can only be reached, never lost
    val milestone = listOf(500, 250, 100, 50, 25, 10).firstOrNull { curatedTotal >= it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", style = MaterialTheme.typography.displayMedium)
        Text(
            "${session.done} kuratiert",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (session.connectionsMade > 0) {
            Text(
                "${session.connectionsMade} neue Verbindungen in deiner Sammlung",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Insgesamt kuratiert: $curatedTotal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        milestone?.let {
            Text(
                "Meilenstein erreicht: $it Einträge ✨",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(onClick = onEnd) { Text("Fertig") }
    }
}

