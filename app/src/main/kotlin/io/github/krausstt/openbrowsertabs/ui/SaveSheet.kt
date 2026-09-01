package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.core.Reactions

/**
 * The save moment.
 *
 * One rule shapes this whole screen: **the link is already saved before it
 * appears.** Nothing here can lose data, so the sheet is free to ask for
 * something without becoming a gate — dismiss it, swipe it away, drop the
 * phone, the entry is in the database either way. Any design where the
 * context question can cost you the save would end the same way every time:
 * you stop sharing.
 *
 * What it asks for is intent, not taxonomy. A tap on 💡 takes about as long
 * as reading this sentence's first word and records the one thing scraping
 * can never recover — why *you* kept it. The text field is there for the
 * times you know exactly what you meant, and it accepts one word.
 */
@Composable
fun SaveSheet(
    host: String,
    title: String?,
    /** Non-null once the same URL was already in the collection. */
    sightings: Int,
    onReact: (String) -> Unit,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // tapping the scrim is a full-value exit, not a cancel
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                // swallow taps so the sheet itself never closes the screen
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Column(
                // the sheet sits above the keyboard: the text field is the
                // whole point of the lower half
                modifier = Modifier.imePadding().navigationBarsPadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                Text(
                    text = if (sightings > 1) "Gespeichert · $sightings. Sichtung" else "Gespeichert",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MonogramTile(host = host, size = 38)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title?.takeIf { it.isNotBlank() } ?: host,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "Warum hebst du das auf?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Reactions.ALL.forEach { r ->
                        ReactionButton(r) { onReact(r.id) }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("…oder in einem Wort") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (note.isNotBlank()) onNote(note) }),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Später") }
                    TextButton(
                        onClick = { onNote(note) },
                        enabled = note.isNotBlank(),
                    ) { Text("Merken") }
                }
            }
        }
    }
}

/**
 * The five stances as a compact row, for places that already have a header
 * of their own (the detail dialog, the stack card).
 */
@Composable
fun ReactionRow(selected: String?, onReact: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Reactions.ALL.forEach { r ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected == r.id) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(46.dp).clickable { onReact(r.id) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(r.emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Emoji over label, sized as a real thumb target rather than a chip. */
@Composable
private fun ReactionButton(reaction: Reactions.Reaction, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(62.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(52.dp).clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(reaction.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            reaction.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
