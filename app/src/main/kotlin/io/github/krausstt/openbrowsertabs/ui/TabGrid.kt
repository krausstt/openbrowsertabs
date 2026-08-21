package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.core.Monogram
import io.github.krausstt.openbrowsertabs.core.Snippets
import io.github.krausstt.openbrowsertabs.data.LinkEntity

/**
 * A saved link as a tab card, shaped like the browser's own tab switcher:
 * a header strip with identity and a dismiss affordance, and a large preview
 * area below.
 *
 * The preview is text, not a picture. Real page thumbnails are a batch-job
 * task (a screenshot per link is neither cheap nor private to fetch at draw
 * time), so until they exist the area shows the page's own words on a tinted
 * surface. That is a placeholder that still answers "what was this?" — a grey
 * rectangle would not.
 */
@Composable
fun TabCard(
    link: LinkEntity,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    compact: Boolean = false,
) {
    val accent = Color(Monogram.color(link.host))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonogramTile(link.host, size = if (compact) 18 else 22)
                Text(
                    text = link.label ?: link.title?.let {
                        Headline.shortHeadline(it, link.canonicalUrl)
                    } ?: link.host,
                    style = if (compact) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 7.dp),
                )
                // archiving is the guilt-free equivalent of closing a tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .size(26.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (compact) 0.95f else 0.78f)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.18f),
                        contentColor = accent,
                    ) {
                        Text(
                            CATEGORY_NAMES[link.category] ?: link.category,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    val preview = link.userSummary ?: link.description
                    Text(
                        text = preview?.takeIf { it.isNotBlank() }
                            ?: "Noch keine Zusammenfassung — beim Antippen nachtragen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 5 else 9,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildList {
                        add(link.host)
                        Snippets.readingMinutes(link.wordCount)?.let { add("$it Min") }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Pinch to change how many cards fit across — the whole of the "2.5D" the
 * user actually asked for: zoom out for the overview, in to read.
 *
 * Only claims the gesture once a second finger is down, so single-finger
 * scrolling in the grid keeps working untouched.
 */
fun Modifier.pinchToZoomColumns(
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 4,
): Modifier = this.pointerInput(columns) {
    var accumulated = 1f
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        accumulated = 1f
        var event: androidx.compose.ui.input.pointer.PointerEvent
        do {
            event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                accumulated *= event.calculateZoom()
                // spreading fingers = fewer, larger cards
                val next = when {
                    accumulated > 1.35f -> columns - 1
                    accumulated < 0.74f -> columns + 1
                    else -> columns
                }.coerceIn(min, max)
                if (next != columns) {
                    onColumnsChange(next)
                    accumulated = 1f
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun ZoomHint(columns: Int) {
    Text(
        text = when (columns) {
            1 -> "1 Spalte · zusammenziehen für Übersicht"
            4 -> "4 Spalten · aufziehen für Details"
            else -> "$columns Spalten · zwei Finger zum Zoomen"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
