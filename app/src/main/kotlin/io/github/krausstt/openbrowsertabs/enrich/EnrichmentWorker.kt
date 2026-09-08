package io.github.krausstt.openbrowsertabs.enrich

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.krausstt.openbrowsertabs.CATEGORY_NAMES
import io.github.krausstt.openbrowsertabs.MainActivity
import io.github.krausstt.openbrowsertabs.R
import io.github.krausstt.openbrowsertabs.TOPIC_NAMES
import io.github.krausstt.openbrowsertabs.core.Categorizer
import io.github.krausstt.openbrowsertabs.core.Enrichment
import io.github.krausstt.openbrowsertabs.core.Headline
import io.github.krausstt.openbrowsertabs.core.Snippets
import io.github.krausstt.openbrowsertabs.core.TextSimilarity
import io.github.krausstt.openbrowsertabs.data.LinkEntity
import io.github.krausstt.openbrowsertabs.data.LinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Drains the pending-enrichment queue: fetch, extract, persist, notify.
 * Human pacing between requests; batches are capped so a single run stays
 * well under WorkManager's 10-minute budget — leftover work re-enqueues
 * itself.
 */
class EnrichmentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    // every step below is blocking: HttpURLConnection reads and SQLite calls.
    // CoroutineWorker defaults to Dispatchers.Default, whose pool is sized to
    // the CPU count — on a 2-core phone a handful of slow fetches can occupy
    // all of it and stall unrelated work.
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = LinkStore(applicationContext)
        val notifyLinkId = inputData.getLong(KEY_LINK_ID, -1L)
        val batch = if (notifyLinkId > 0) {
            listOfNotNull(store.byId(notifyLinkId)).filter { it.enrichmentState == "pending" }
        } else {
            store.pendingEnrichment(BATCH_SIZE)
        }

        var sawTransient = false
        batch.forEachIndexed { index, link ->
            if (index > 0) delay(Random.nextLong(2_000, 4_500))
            sawTransient = enrichOne(store, link, notify = link.id == notifyLinkId) || sawTransient
        }

        // keep draining bulk imports in follow-up runs
        if (notifyLinkId <= 0 && store.pendingEnrichmentCount() > 0 && !sawTransient) {
            enqueueDrain(applicationContext)
        }
        if (sawTransient) Result.retry() else Result.success()
    }

    /** @return true if the outcome was transient (leave pending, retry run) */
    private fun enrichOne(store: LinkStore, link: LinkEntity, notify: Boolean): Boolean {
        // search queries have no fetchable article — label already says it all
        if (link.category == "search_query") {
            store.applyEnrichment(link.id, state = "done", title = link.label)
            computeRelated(store, link.id)
            return false
        }
        return when (val outcome = Enrichment.enrich(link.canonicalUrl)) {
            is Enrichment.Outcome.Enriched -> {
                val a = outcome.article

                // A consent wall is served *instead of* the article and
                // extracts cleanly, so it would otherwise be stored as the
                // page's content and poison both the summary and the
                // similarity corpus. Keep the slug-derived title, drop the text.
                if (Snippets.isConsentWall(a.text) || Snippets.isConsentWall(a.description)) {
                    store.applyEnrichment(
                        link.id,
                        state = "unfetchable",
                        title = Headline.best(a.title, outcome.finalUrl, a.siteName),
                        siteName = a.siteName,
                        imageUrl = a.imageUrl,
                        topics = Categorizer.topics(
                            listOfNotNull(a.title, outcome.finalUrl).joinToString(" "),
                        ),
                    )
                    if (notify) {
                        postNotification(
                            link.id,
                            title = Headline.best(a.title, outcome.finalUrl, a.siteName),
                            text = "Gespeichert — hinter einem Cookie-Banner, kein Artikeltext. " +
                                "Du kannst im Detail eine eigene Zusammenfassung eintragen.",
                        )
                    }
                    return false
                }
                // catchy one-liner: page description, unless it's a fixed
                // site-wide default (e.g. HuggingFace's identical tagline on
                // every model page) or sponsor/affiliate-heavy (common in
                // YouTube descriptions) — then fall back to the article's
                // own lead sentences (the PrismML case: og:description missing)
                val rawDescription = a.description
                val useRawDescription = rawDescription != null &&
                    store.countSharedDescription(rawDescription, link.id) < 2 &&
                    !Snippets.isPromotional(rawDescription)
                val oneLiner = rawDescription.takeIf { useRawDescription } ?: Snippets.lead(a.text, 200)

                // topics from real content beat URL-slug guessing, especially
                // for opaque URLs like youtube.com/watch?v=<id>
                val topicSource = listOfNotNull(a.title, oneLiner, a.text?.take(2000))
                    .joinToString(" ")
                val refinedTopics = Categorizer.topics(topicSource)

                store.applyEnrichment(
                    link.id, state = "done",
                    // publisher-only titles ("Golem") fall back to the URL slug
                    title = Headline.best(a.title, outcome.finalUrl, a.siteName),
                    description = oneLiner, content = a.text,
                    siteName = a.siteName, publishedAt = a.publishedAt,
                    topics = refinedTopics,
                    imageUrl = a.imageUrl,
                    wordCount = Snippets.wordCount(a.text),
                )
                val related = computeRelated(store, link.id)
                if (notify) {
                    postNotification(
                        link.id,
                        title = Headline.shortHeadline(a.title, outcome.finalUrl),
                        text = buildNotificationText(store, link, oneLiner, related),
                    )
                }
                false
            }
            is Enrichment.Outcome.Unfetchable -> {
                store.applyEnrichment(link.id, state = "unfetchable")
                if (notify) {
                    postNotification(
                        link.id,
                        title = link.title ?: link.host,
                        text = "Gespeichert — Seite nicht abrufbar (${outcome.reason}), " +
                            "Anreicherung folgt später",
                    )
                }
                false // terminal state, no retry
            }
            is Enrichment.Outcome.Transient -> true
        }
    }

    /** Compute + persist top-3 related links; returns them for the notification. */
    private fun computeRelated(store: LinkStore, linkId: Long): List<LinkEntity> {
        val docs = store.similarityDocs().map { (id, text) -> TextSimilarity.Doc(id, text) }
        val related = TextSimilarity.topRelated(docs, targetId = linkId, k = 3)
        store.updateRelated(linkId, related.map { it.id })
        return store.byIds(related.map { it.id })
    }

    private fun buildNotificationText(
        store: LinkStore,
        link: LinkEntity,
        oneLiner: String?,
        related: List<LinkEntity>,
    ): String {
        val fresh = store.byId(link.id) ?: link
        val lines = mutableListOf<String>()
        oneLiner?.let { lines.add(it) }
        val tags = buildList {
            add(CATEGORY_NAMES[fresh.category] ?: fresh.category)
            fresh.topics.filter { it != "untagged" }.take(2)
                .forEach { add(TOPIC_NAMES[it] ?: it) }
        }
        lines.add("🏷 " + tags.joinToString(" · "))
        if (related.isNotEmpty()) {
            val names = related.joinToString(" · ") { r ->
                (r.label ?: r.title ?: r.host).take(45)
            }
            lines.add("🔗 Hängt zusammen mit: $names")
        }
        return lines.joinToString("\n")
    }

    private fun postNotification(linkId: Long, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(ctx)
        // deep link straight to the entry instead of the app's front door
        val tap = PendingIntent.getActivity(
            ctx, linkId.toInt(),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("openbrowsertabs://link/$linkId"))
                .setClass(ctx, MainActivity::class.java)
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(linkId.toInt(), notification)
    }

    companion object {
        private const val KEY_LINK_ID = "link_id"
        private const val BATCH_SIZE = 15
        private const val CHANNEL_ID = "enrichment"
        private const val DRAIN_WORK = "enrichment-drain"

        /** Enrich one just-shared link ASAP and notify about the result. */
        fun enqueueForLink(context: Context, linkId: Long) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<EnrichmentWorker>()
                    .setInputData(workDataOf(KEY_LINK_ID to linkId))
                    .build(),
            )
        }

        /** Drain the whole pending queue (bulk imports); chains itself. */
        fun enqueueDrain(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                DRAIN_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EnrichmentWorker>().build(),
            )
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Anreicherung",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Ergebnis der Link-Anreicherung nach dem Teilen"
                },
            )
        }
    }
}
