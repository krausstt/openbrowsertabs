package io.github.krausstt.openbrowsertabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.core.Monogram
import io.github.krausstt.openbrowsertabs.core.Reactions
import io.github.krausstt.openbrowsertabs.core.Snippets
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import java.util.Calendar

/**
 * One (thumbnail, title, link, metadata) tuple. The monogram gives every row
 * a stable visual anchor without a network request; tags are shown inline
 * because they are the primary navigation, not decoration.
 */
@Composable
fun LinkCard(
    link: LinkEntity,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            MonogramTile(link.host)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = Reactions.emojiOf(link.reaction).let { if (it.isEmpty()) "" else "$it " } +
                        (link.label?.let { "🔍 $it" }
                            ?: link.title?.let { Headline.shortHeadline(it, link.canonicalUrl) }
                            ?: link.host),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = link.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )

                val tags = link.allTags.take(3)
                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        tags.forEach { tag ->
                            TagChip(tag = tag, onClick = { onTagClick(tag) })
                        }
                    }
                }

                Text(
                    text = metaLine(link),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun MonogramTile(host: String, size: Int = 46) {
    val color = Color(Monogram.color(host))
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Monogram.initials(host),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.34f).sp,
        )
    }
}

@Composable
fun TagChip(tag: String, onClick: () -> Unit, selected: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = "#" + (TOPIC_NAMES[tag] ?: tag),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

/** "Artikel · 4 Min · vor 3 Tagen" — type, effort, recency, in that order. */
private fun metaLine(link: LinkEntity): String = buildList {
    // the note goes first: it is the only line on the card the human wrote
    link.userNote?.let { add("„$it“") }
    add(CATEGORY_NAMES[link.category] ?: link.category)
    Snippets.readingMinutes(link.wordCount)?.let { add("$it Min") }
    add(relativeDay(link.lastSeenAt))
    if (link.enrichmentState == "pending") add("⏳")
    if (link.enrichmentState == "unfetchable") add("⚠")
}.joinToString(" · ")

private fun relativeDay(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val days = ((now.timeInMillis - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "heute"
        days == 1 -> "gestern"
        days < 7 -> "vor $days Tagen"
        days < 30 -> "vor ${days / 7} Wo."
        else -> "${then.get(Calendar.DAY_OF_MONTH)}.${then.get(Calendar.MONTH) + 1}."
    }
}

/** Compact section heading used across the screens. */
@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (onTrailingClick != null) {
                    Modifier.clickable(onClick = onTrailingClick)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

internal val ScreenPadding = PaddingValues(horizontal = 14.dp)
